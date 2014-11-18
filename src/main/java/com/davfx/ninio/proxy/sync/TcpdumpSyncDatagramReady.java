package com.davfx.ninio.proxy.sync;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;

public final class TcpdumpSyncDatagramReady implements Ready {
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpdumpSyncDatagramReady.class);

	private static final double WAIT_ON_TCPDUMP_ENDED = 5d;
	private static final int MAX_PACKET_SIZE = 2048;

	public static final class Receiver {
		private DatagramSocket socket = null;
		private final Object lock = new Object();
		private final Map<String, ReadyConnection> connections = new ConcurrentHashMap<>();
		public Receiver(final String interfaceId, final int port) {
			Executors.newSingleThreadExecutor().execute(new Runnable() {
				@Override
				public void run() {
					while (true) {
						File dir = new File(".");

						List<String> toExec = new LinkedList<String>();
						File tcpdump = new File(dir, "tcpdump");
						if (tcpdump.exists()) {
							toExec.add(tcpdump.getAbsolutePath());
						} else {
							toExec.add("tcpdump");
						}
						toExec.add("-w");
						toExec.add("/dev/stdout");
						toExec.add("-i");
						toExec.add(interfaceId);
						toExec.add("-n");
						toExec.add("-K");
						toExec.add("-p");
						toExec.add("-q");
						toExec.add("-s");
						toExec.add(String.valueOf(MAX_PACKET_SIZE));
						// toExec.add("-U"); // Unbuffers output
						toExec.add("dst");
						toExec.add("port");
						toExec.add(String.valueOf(port));
						
						ProcessBuilder pb = new ProcessBuilder(toExec);
						pb.directory(dir);
						Process p;
						try {
							LOGGER.info("Executing tcpdump in: {}", dir.getCanonicalPath());
							p = pb.start();
						} catch (IOException e) {
							LOGGER.error("Could not run tcpdump", e);
							p = null;
						}
						
						if (p != null) {
							final InputStream error = p.getErrorStream();
							Executors.newSingleThreadExecutor().execute(new Runnable() {
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
							Executors.newSingleThreadExecutor().execute(new Runnable() {
								@Override
								public void run() {
									try {
										DataInputStream in = new DataInputStream(input);
										try {
											long header = readIntLittleEndian(in);
											if (header != 0xA1B2C3D4) {
												throw new IOException("Bad header");
											}
											skip(in, 20);
					
											while (true) {
												@SuppressWarnings("unused")
												double timestamp = ((double) readIntLittleEndian(in)) + (((double) readIntLittleEndian(in)) / 1000d); // sec, usec
												
												int packetSize = (int) readIntLittleEndian(in);
												skip(in, 4);
					
												skip(in, 12);
					
												int ipHeader = readShortBigEndian(in);
												if (ipHeader != 0x800) {
													// Not IP
													skip(in, packetSize - 12 - 2);
													continue;
												}
												
												skip(in, 9);
												
												int type = readByte(in);
												if (type != 17) {
													// 17 UDP, 6 TCP
													skip(in, packetSize - 12 - 2 - 9 - 1);
													continue;
												}
										
												skip(in, 2);
										
												String sourceIp = readIpV4(in);
												String destinationIp = readIpV4(in);
												int sourcePort = readShortBigEndian(in);
												int destinationPort = readShortBigEndian(in);
					
												skip(in, 4);
												
												byte[] data = new byte[packetSize - 12 - 2 - 9 - 1 - 2 - 4 - 4 - 2 - 2 - 4];
												in.readFully(data);
												
												LOGGER.trace("Packet received: {}:{} -> {}:{}", sourceIp, sourcePort, destinationIp, destinationPort);
												
												ReadyConnection connection = connections.get(key(destinationIp, destinationPort));
												if (connection != null) {
													connection.handle(null, ByteBuffer.wrap(data, 0, data.length));
												}
											}
										} finally {
											in.close();
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
					}
				}
			});
		}
		
		private static final String key(String host, int port) {
			try {
				return InetAddress.getByName(host).getHostAddress() + ":" + port;
			} catch (UnknownHostException e) {
				LOGGER.warn("Could not determine IP of " + host);
				return host + ":" + port;
			}
		}
		
		private boolean add(Address address, ReadyConnection connection) {
			synchronized (lock) {
				while (socket == null) {
					try {
						lock.wait();
					} catch (InterruptedException ie) {
					}
				}
			}

			String key = key(address.getHost(), address.getPort());
			
			if (connections.containsKey(key)) {
				connection.failed(new IOException("Could not open simultaneous connections to: " + address));
				return false;
			}
			
			connections.put(key, connection);
			return true;
		}
		
		private void remove(Address address) {
			connections.remove(key(address.getHost(), address.getPort()));
		}
		
		private boolean send(Address address, ByteBuffer buffer) {
			try {
				DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.capacity(), InetAddress.getByName(address.getHost()), address.getPort());
				socket.send(packet);
			} catch (IOException ioe) {
				LOGGER.error("Could not send UDP packet", ioe);
				return false;
			}
			return true;
		}
	}

	private final Receiver receiver;
	public TcpdumpSyncDatagramReady(Receiver receiver) {
		this.receiver = receiver;
	}
	
	@Override
	public void connect(final Address address, final ReadyConnection connection) {
		if (!receiver.add(address, connection)) {
			return;
		}
		
		connection.connected(new FailableCloseableByteBufferHandler() {
			@Override
			public void failed(IOException e) {
				receiver.remove(address);
			}
			@Override
			public void close() {
				receiver.remove(address);
			}
			@Override
			public void handle(Address a, ByteBuffer buffer) {
				if (!receiver.send(address, buffer)) {
					connection.failed(new IOException("Failed sending UDP packet"));
				}
			}
		});
	}

	private static String readIpV4(DataInputStream in) throws IOException {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < 4; i++) {
			int k = readByte(in) & 0xFF;
			if (b.length() > 0) {
				b.append('.');
			}
			b.append(String.valueOf(k));
		}
		return b.toString();
	}

	private static long readIntLittleEndian(DataInputStream in) throws IOException {
		byte[] b = new byte[4];
		in.readFully(b);
		ByteBuffer bb = ByteBuffer.wrap(b);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		return bb.getInt() & 0xFFFFFFFF;
	}
	private static int readShortBigEndian(DataInputStream in) throws IOException {
		return in.readShort() & 0xFFFF;
	}
	private static int readByte(DataInputStream in) throws IOException {
		return in.readByte() & 0xFF;
	}
	private static long skip(DataInputStream in, long nn) throws IOException {
		long n = nn;
		while (n > 0L) {
			int r = in.read();
			if (r < 0) {
				throw new IOException("Could not skip " + n + " bytes");
			}
			n--;
		}
		return nn;
	}
}
