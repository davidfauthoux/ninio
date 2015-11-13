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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.ClassThreadFactory;
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
		
		private final Map<String, ReadyConnection> connections = new ConcurrentHashMap<>();
		
		private DatagramSocket socket = null;
		
		public Receiver(final Rule rule, final String interfaceId) { //, final boolean promiscuous) {
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

			Executors.newSingleThreadExecutor(new ClassThreadFactory(TcpdumpSyncDatagramReady.class)).execute(new Runnable() {
				@Override
				public void run() {
					final TcpdumpReader tcpdumpReader = RAW ? new RawTcpdumpReader(interfaceId.equals("any")) : new HexTcpdumpReader();
					
					while (true) {
						File dir = new File(".");
			
						List<String> toExec = new LinkedList<String>();
						File tcpdump = new File(dir, "tcpdump");
						if (tcpdump.exists()) {
							toExec.add(tcpdump.getAbsolutePath());
						} else {
							toExec.add("tcpdump");
						}
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
						
						if (p != null) {
							final InputStream error = p.getErrorStream();
							Executors.newSingleThreadExecutor(new ClassThreadFactory(TcpdumpSyncDatagramReady.class, "err")).execute(new Runnable() {
								@Override
								public void run() {
									try {
										BufferedReader r = new BufferedReader(new InputStreamReader(error));
										while (true) {
											String line = r.readLine();
											if (line == null) {
												break;
											}
											LOGGER.warn("Tcpdump message: {}", line);
										}
									} catch (IOException e) {
										LOGGER.error("Error in tcpdump process", e);
									}
								}
							});
							
							final InputStream input = p.getInputStream();
							Executors.newSingleThreadExecutor(new ClassThreadFactory(TcpdumpSyncDatagramReady.class, "in")).execute(new Runnable() {
								@Override
								public void run() {
									try {
										try {
											tcpdumpReader.read(input, new TcpdumpReader.Handler() {
												@Override
												public void handle(double timestamp, Address sourceAddress, Address destinationAddress, ByteBuffer buffer) {
													/*%% if (output != null) {
														try {
															output.writeInt(buffer.remaining());
															output.write(buffer.array(), buffer.position(), buffer.remaining());
															output.flush();
														} catch (IOException ee) {
															LOGGER.error("Could not forward tcpdump packet", ee);
														}
													}*/
													
													ReadyConnection connection = connections.get("s/" + sourceAddress.getHost() + "/"); //%% new Address(destinationIp, destinationPort));
													if (connection == null) {
														connection = connections.get("d/" + destinationAddress.getHost() + "/" + destinationAddress.getPort()); //%% new Address(null, destinationPort));
													}
													if (connection == null) {
														connection = connections.get("s//" + destinationAddress.getPort()); //%% new Address(null, destinationPort));
													}
													
													if (connection != null) {
														connection.handle(sourceAddress, buffer);
													} else {
														LOGGER.trace("No match for packet: {} -> {}, available: {}", sourceAddress, destinationAddress, connections.keySet());
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
						}
						
						try {
							Thread.sleep((long) (WAIT_ON_TCPDUMP_ENDED * 1000d));
						} catch (InterruptedException ie) {
						}
						LOGGER.warn("Tcpdump has ended");
					}
				}
			});
		}
		
		private void add(String key, ReadyConnection connection) {
			connections.put(key, connection);
		}
		
		private void remove(String key) {
			connections.remove(key);
		}

		public Receiver prepare() throws IOException {
			DatagramSocket s = new DatagramSocket();
			try {
				s.setReceiveBufferSize(READ_BUFFER_SIZE);
				s.setSendBufferSize(WRITE_BUFFER_SIZE);
				LOGGER.debug("Datagram socket buffer size, receive {}, send {}", s.getReceiveBufferSize(), s.getSendBufferSize());
			} catch (IOException ioe) {
				s.close();
				throw ioe;
			}
			socket = s;
			return this;
		}
		
		@Override
		public void close() {
			if (socket != null) {
				socket.close();
				socket = null;
			}
		}
	}
	
	private final Receiver receiver;
	private boolean bind = false;
	
	public TcpdumpSyncDatagramReady(Receiver receiver) {
		this.receiver = receiver;
	}
	
	public TcpdumpSyncDatagramReady bind() {
		bind = true;
		return this;
	}
	
	@Override
	public void connect(final Address address, final ReadyConnection connection) {
		final DatagramSocket socket;
		final String key;
		final boolean haveToCloseSocket;
		if (bind) {
			try {
				InetSocketAddress a = AddressUtils.toBindableInetSocketAddress(address);
				if (a == null) {
					throw new IOException("Invalid address");
				}
				socket = new DatagramSocket(a);
				try {
					socket.setReceiveBufferSize(READ_BUFFER_SIZE);
					socket.setSendBufferSize(WRITE_BUFFER_SIZE);
					LOGGER.debug("Datagram bound socket {} buffer size, receive {}, send {}", a, socket.getReceiveBufferSize(), socket.getSendBufferSize());
				} catch (IOException ioee) {
					socket.close();
					throw ioee;
				}
				haveToCloseSocket = true;
			} catch (IOException ioe) {
				connection.failed(ioe);
				return;
			}
			key = "d/" + address.getHost() + "/" + address.getPort();
		} else {
			if (receiver.socket == null) {
				try {
					socket = new DatagramSocket();
					try {
						socket.setReceiveBufferSize(READ_BUFFER_SIZE);
						socket.setSendBufferSize(WRITE_BUFFER_SIZE);
						LOGGER.debug("Datagram socket buffer size, receive {}, send {}", socket.getReceiveBufferSize(), socket.getSendBufferSize());
					} catch (IOException ioee) {
						socket.close();
						throw ioee;
					}
					haveToCloseSocket = true;
				} catch (IOException ioe) {
					connection.failed(ioe);
					return;
				}
				key = "s//" + socket.getLocalPort();
			} else {
				socket = receiver.socket;
				key = "s/" + address.getHost() + "/";
				haveToCloseSocket = false;
			}
		}
		
		receiver.add(key, connection);

		connection.connected(new FailableCloseableByteBufferHandler() {
			private void closeSocket() {
				receiver.remove(key);
				if (haveToCloseSocket) {
					socket.close();
				}
			}
			
			@Override
			public void failed(IOException e) {
				closeSocket();
			}
			@Override
			public void close() {
				closeSocket();
			}
			@Override
			public void handle(Address a, ByteBuffer buffer) {
				if (a == null) {
					LOGGER.error("Dropping datagram sent to null address"); //%% , using instead: {}", address);
					return;
					//%% a = address;
				}
				LOGGER.trace("Sending datagram to: {}", a);
				try {
					DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.capacity(), InetAddress.getByName(a.getHost()), a.getPort());
					socket.send(packet);
				} catch (IOException ioe) {
					LOGGER.trace("Error while sending datagram to: {}", a, ioe);
					closeSocket();
					connection.failed(ioe);
				}
			}
		});
	}


}
