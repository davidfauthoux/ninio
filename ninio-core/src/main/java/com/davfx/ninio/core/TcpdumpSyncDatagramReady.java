package com.davfx.ninio.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ClassThreadFactory;
import com.google.common.base.Joiner;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

// sudo sysctl -w net.core.rmem_max=8388608
// sudo sysctl -w net.core.wmem_max=8388608
// sudo sysctl -w net.core.rmem_default=8388608
// sudo sysctl -w net.core.wmem_default=8388608
// sudo sysctl -w net.ipv4.route.flush=1

// F*cking apparmor: http://unix.stackexchange.com/questions/88253/permission-denied-when-writing-to-dev-stdout
public final class TcpdumpSyncDatagramReady implements Ready {
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpdumpSyncDatagramReady.class);

	private static final double WAIT_ON_TCPDUMP_ENDED = 5d;
	
	private static final Config CONFIG = ConfigFactory.load(TcpdumpSyncDatagramReady.class.getClassLoader());
	//%% private static final String DO_OUTPUT = CONFIG.hasPath("ninio.tcpdump.output") ? CONFIG.getString("ninio.tcpdump.output") : null;

	private static final boolean RAW;
	static {
		String mode = CONFIG.getString("ninio.tcpdump.mode");
		if (mode.equals("raw")) {
			RAW = true;
		} else if (mode.equals("hex")) {
			RAW = false;
		} else {
			throw new ConfigException.BadValue("ninio.tcpdump.mode", "Invalid: " + mode + ", only 'raw' and 'hex' allowed");
		}
	}
	private static final String TCPDUMP_COMMAND = CONFIG.getString("ninio.tcpdump.path");
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("ninio.tcpdump.datagram.read.size").intValue();
	private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("ninio.tcpdump.datagram.write.size").intValue();

	public static interface Rule {
		Iterable<String> parameters();
	}
	
	public static final class EmptyRule implements Rule {
		public EmptyRule() {
		}
		@Override
		public Iterable<String> parameters() {
			return new LinkedList<>();
		}
	}
	
	public static final class SourcePortRule implements Rule {
		private final List<String> params = new LinkedList<>();
		public SourcePortRule(int port) {
			params.add("src");
			params.add("port");
			params.add(String.valueOf(port));
		}
		@Override
		public Iterable<String> parameters() {
			return params;
		}
	}
	
	public static final class DestinationPortRule implements Rule {
		private final List<String> params = new LinkedList<>();
		public DestinationPortRule(int port) {
			params.add("dst");
			params.add("port");
			params.add(String.valueOf(port));
		}
		@Override
		public Iterable<String> parameters() {
			return params;
		}
	}
	
	public static final class SourcePortRangeRule implements Rule {
		private final List<String> params = new LinkedList<>();
		public SourcePortRangeRule(int fromPort, int toPort) { // toPort inclusive
			params.add("src");
			params.add("portrange");
			params.add(fromPort + "-" + toPort);
		}
		@Override
		public Iterable<String> parameters() {
			return params;
		}
	}
	
	public static final class DestinationPortRangeRule implements Rule {
		private final List<String> params = new LinkedList<>();
		public DestinationPortRangeRule(int fromPort, int toPort) { // toPort inclusive
			params.add("dst");
			params.add("portrange");
			params.add(fromPort + "-" + toPort);
		}
		@Override
		public Iterable<String> parameters() {
			return params;
		}
	}
	
	public static final class Receiver implements AutoCloseable, Closeable {
		//%% private final File outputFile;
		//%% private final DataOutputStream output;
		
		private static void execute(String name, Runnable runnable) {
			new ClassThreadFactory(TcpdumpSyncDatagramReady.class, name).newThread(runnable).start();
		}
		
		private final ConcurrentLinkedQueue<ReadyConnection> connections = new ConcurrentLinkedQueue<>();
		private DatagramSocket socket = null;
		private Process process = null;
		private boolean closed = false;
		
		public Receiver(final Rule rule, final String interfaceId, final Address address) throws IOException { //, final boolean promiscuous) {
			/*%%
			if (DO_OUTPUT != null) {
				outputFile = new File(DO_OUTPUT);
				DataOutputStream o;
				try {
					o = new DataOutputStream(new FileOutputStream(outputFile));
				} catch (IOException ioe) {
					LOGGER.error("Could not create output stream", ioe);
					o = null;
				}
				output = o;
			} else {
				outputFile = null;
				output = null;
			}
			*/

			execute(null, new Runnable() {
				@Override
				public void run() {
					final TcpdumpReader tcpdumpReader = RAW ? new RawTcpdumpReader(interfaceId.equals("any")) : new HexTcpdumpReader();
					
					while (true) {
						File dir = new File(".");
			
						List<String> toExec = new LinkedList<String>();
						toExec.add(TCPDUMP_COMMAND);
						//%% toExec.add("-w");
						//%% toExec.add("-"); // Try with /dev/stdout
						toExec.add("-i");
						toExec.add(interfaceId);
						toExec.add("-nn");
						for (String o : tcpdumpReader.tcpdumpOptions()) {
							toExec.add(o);
						}
						toExec.add("-K");
						// if (!promiscuous) {
						toExec.add("-p");
						// }
						toExec.add("-q");
						toExec.add("-s");
						toExec.add("0");
						// toExec.add("-U"); // Unbuffers output
						for (String p : rule.parameters()) {
							toExec.add(p);
						}
						
						ProcessBuilder pb = new ProcessBuilder(toExec);
						pb.directory(dir);
						Process p;
						try {
							LOGGER.info("In: {}, executing: {}", dir.getCanonicalPath(), Joiner.on(' ').join(toExec));
							p = pb.start();
						} catch (IOException e) {
							LOGGER.error("Could not run tcpdump", e);
							p = null;
						}
						
						DatagramSocket s;
						try {
							InetSocketAddress a = AddressUtils.toBindableInetSocketAddress(address);
							if (a == null) {
								throw new IOException("Invalid address");
							}
							s = new DatagramSocket(a);
							try {
								s.setReceiveBufferSize(READ_BUFFER_SIZE);
								s.setSendBufferSize(WRITE_BUFFER_SIZE);
								LOGGER.debug("Datagram bound socket {} ({}) buffer size, receive {}, send {}", a, s.getLocalPort(), s.getReceiveBufferSize(), s.getSendBufferSize());
							} catch (IOException se) {
								s.close();
								throw se;
							}
						} catch (IOException ae) {
							LOGGER.error("Could not create receiver socket", ae);
							s = null;
						}
						
						synchronized (TcpdumpSyncDatagramReady.Receiver.this) {
							if (closed) {
								s.close();
								p.destroy();
								break;
							}
							socket = s;
							process = p;
							TcpdumpSyncDatagramReady.Receiver.this.notifyAll();
						}
						
						if (p != null) {
							final InputStream error = p.getErrorStream();
							execute("err", new Runnable() {
								@Override
								public void run() {
									try {
										try {
											BufferedReader r = new BufferedReader(new InputStreamReader(error));
											while (true) {
												String line = r.readLine();
												if (line == null) {
													break;
												}
												LOGGER.debug("Tcpdump message: {}", line);
											}
										} finally {
											error.close();
										}
									} catch (IOException e) {
										LOGGER.error("Error in tcpdump process", e);
									}
								}
							});
							
							final InputStream input = p.getInputStream();
							execute("in", new Runnable() {
								@Override
								public void run() {
									try {
										try {
											tcpdumpReader.read(input, new TcpdumpReader.Handler() {
												@Override
												public void handle(double timestamp, Address sourceAddress, Address destinationAddress, ByteBuffer buffer) {
													LOGGER.debug("---->{}", buffer.remaining());
													for (ReadyConnection c : connections) {
														c.handle(sourceAddress, buffer);
													}
												}
											});
										} finally {
											input.close();
										}
									} catch (IOException e) {
										LOGGER.error("Error in tcpdump process", e);
									}
								}
							});
							
							int code;
							try {
								code = p.waitFor();
							} catch (InterruptedException e) {
								code = -1;
							}
							if (code != 0) {
								LOGGER.error("Non zero return code from tcpdump: {}", code);
							}
							try {
								error.close();
							} catch (IOException e) {
							}
							try {
								input.close();
							} catch (IOException e) {
							}
							p.destroy();
							synchronized (TcpdumpSyncDatagramReady.Receiver.this) {
								process = null;
								if (closed) {
									break;
								}
							}
						}
						
						try {
							Thread.sleep((long) (WAIT_ON_TCPDUMP_ENDED * 1000d));
						} catch (InterruptedException ie) {
						}
						LOGGER.warn("Tcpdump has ended");
					}
				}
			});
			
			synchronized (this) {
				while (process == null) {
					try {
						wait();
					} catch (InterruptedException e) {
					}
				}
			}
		}
		
		@Override
		public void close() {
			closed = true;
			synchronized (this) {
				if (process != null) {
					process.destroy();
					process = null;
				}
				if (socket != null) {
					socket.close();
					socket = null;
				}
			}
		}
	}
	
	private final Receiver receiver;
	
	public TcpdumpSyncDatagramReady(Receiver receiver) {
		this.receiver = receiver;
	}
	
	@Override
	public void connect(final Address address, final ReadyConnection connection) {
		if (receiver == null) {
			connection.failed(new IOException("Invalid receiver"));
			return;
		}
		
		receiver.connections.add(connection);

		connection.connected(new FailableCloseableByteBufferHandler() {
			private void remove() {
				receiver.connections.remove(connection);
			}
			@Override
			public void failed(IOException e) {
				remove();
			}
			@Override
			public void close() {
				remove();
			}
			@Override
			public void handle(Address a, ByteBuffer buffer) {
				if (a == null) {
					a = address;
				}
				LOGGER.trace("Sending datagram to: {}", a);
				try {
					DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.capacity(), InetAddress.getByName(a.getHost()), a.getPort());
					DatagramSocket s = receiver.socket;
					if (s == null) {
						LOGGER.trace("Could not send datagram to: {}, socket not yet open", a);
					} else {
						s.send(packet);
					}
				} catch (IOException ioe) {
					LOGGER.trace("Error while sending datagram to: {}", a, ioe);
					remove();
					connection.failed(ioe);
				}
			}
		});
	}


}
