package com.davfx.ninio.common;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
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
import com.davfx.ninio.common.ClassThreadFactory;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.util.ConfigUtils;
import com.davfx.util.DateUtils;
import com.typesafe.config.Config;

// F*cking apparmor: http://unix.stackexchange.com/questions/88253/permission-denied-when-writing-to-dev-stdout
public final class TcpdumpSyncDatagramReady implements Ready {
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpdumpSyncDatagramReady.class);

	private static final double WAIT_ON_TCPDUMP_ENDED = 5d;
	private static final Config CONFIG = ConfigUtils.load(TcpdumpSyncDatagramReady.class);
	private static final int MAX_PACKET_SIZE = CONFIG.getBytes("tcpdump.packet.size").intValue();
	private static final String DO_OUTPUT = CONFIG.hasPath("tcpdump.output") ? CONFIG.getString("tcpdump.output") : null;

	private static final int IP_HEADER_LENGTH_MONO = System.getProperty("os.name").equals("Mac OS X") ? CONFIG.getInt("tcpdump.packet.header.mono.macosx") : CONFIG.getInt("tcpdump.packet.header.mono.default");
	private static final int IP_HEADER_LENGTH_MULTI = System.getProperty("os.name").equals("Mac OS X") ? CONFIG.getInt("tcpdump.packet.header.multi.macosx") : CONFIG.getInt("tcpdump.packet.header.multi.default");

	private static final int UDP_ERROR_LIMIT = 100 * 1024;
	
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
	
	public static final class Receiver {
		private final File outputFile;
		private final DataOutputStream output;
		
		private final Map<Address, ReadyConnection> connections = new ConcurrentHashMap<>();
		
		public Receiver(final Rule rule, final String interfaceId, final boolean promiscuous) {
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

			Executors.newSingleThreadExecutor(new ClassThreadFactory(TcpdumpSyncDatagramReady.class)).execute(new Runnable() {
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
						toExec.add("-"); //%%%%%%% "/dev/stdout");
						toExec.add("-i");
						toExec.add(interfaceId);
						toExec.add("-n");
						toExec.add("-K");
						if (!promiscuous) {
							toExec.add("-p");
						}
						toExec.add("-q");
						toExec.add("-s");
						toExec.add(String.valueOf(MAX_PACKET_SIZE));
						toExec.add("-U"); // Unbuffers output
						for (String p : rule.parameters()) {
							toExec.add(p);
						}
						
						final int headerSize = interfaceId.equals("any") ? IP_HEADER_LENGTH_MULTI : IP_HEADER_LENGTH_MONO;
						
						ProcessBuilder pb = new ProcessBuilder(toExec);
						pb.directory(dir);
						Process p;
						try {
							LOGGER.info("Executing {} in: {}", toExec, dir.getCanonicalPath());
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
										DataInputStream in = new DataInputStream(input);
										try {
											LOGGER.debug("Reading tcpdump stream");
											long header = readIntLittleEndian(in);
											//%%%% System.out.println("HEADER = " + header);
											if (header != 0xA1B2C3D4) {
												throw new IOException("Bad header: 0x" + Long.toHexString(header));
											}
											LOGGER.debug("Tcpdump header recognized");
											skip(in, 20);
											/*
											https://wiki.wireshark.org/Development/LibpcapFileFormat
											
											typedef struct pcap_hdr_s {
												guint32 magic_number;   /* magic number * /
												guint16 version_major;  /* major version number * /
												guint16 version_minor;  /* minor version number * /
												gint32  thiszone;       /* GMT to local correction * /
												guint32 sigfigs;        /* accuracy of timestamps * /
												guint32 snaplen;        /* max length of captured packets, in octets * /
												guint32 network;        /* data link type * /
											} pcap_hdr_t;
											*/
					
											LOGGER.debug("Entering tcpdump loop");
											while (true) {
												LOGGER.trace("Tcpdump loop, step 1");
												//%%% System.out.println("IN WHILE");
												/*
												typedef struct pcaprec_hdr_s {
													guint32 ts_sec;         /* timestamp seconds * /
													guint32 ts_usec;        /* timestamp microseconds * /
													guint32 incl_len;       /* number of octets of packet saved in file * /
													guint32 orig_len;       /* actual length of packet * /
												} pcaprec_hdr_t;
												*/
												double timestamp = ((double) readIntLittleEndian(in)) + (((double) readIntLittleEndian(in)) / 1000000d); // sec, usec
												//%%% System.out.println("TIMESTAMP = " + timestamp);
												
												LOGGER.trace("Tcpdump loop, step 2");
												int packetSize = (int) readIntLittleEndian(in); //%%%%% - 8; // -8 because length includes packetSize & actualPacketSize
												//%%%% System.out.println("packetSize=" + packetSize);
												int remaining = packetSize;

												if (remaining < 4) {
													LOGGER.debug("Invalid packet (remaining {} bytes)", remaining);
													skip(in, remaining);
													continue;
												}

												LOGGER.trace("Tcpdump loop, step 3");

												@SuppressWarnings("unused")
												int actualPacketSize = (int) readIntLittleEndian(in); //%%%%% - 8; // -8 because length includes packetSize & actualPacketSize
												//%%%% System.out.println("actualPacketSize=" + actualPacketSize);
												
												if (remaining < headerSize) {
													LOGGER.debug("Invalid packet (remaining {} bytes)", remaining);
													skip(in, remaining);
													continue;
												}
												skip(in, headerSize);
												remaining -= headerSize;

												/*%%%%%%%%%%
												for (int i = 0; i < Integer.parseInt(System.getProperty("l")); i++) { //16 on Mac //26 on linux
													System.out.println("__ = " + Integer.toHexString(in.readByte() & 0xFF));
												}
												System.out.println("__ = " + Integer.toHexString(in.readInt()));
												System.out.println("__ = " + Integer.toHexString(in.readInt()));

												System.out.println("VERSION&HDR SHOULD BE 0x40 = " + Integer.toHexString(in.readByte()));
												System.out.println("TYPE SHOULD BE 0x0 = " + in.readByte());
												int lll = in.readShort();
												System.out.println("LENGTH = " + lll);
												System.out.println("IDENTIFICATION = " + in.readShort());
												System.out.println("FLAGS = " + in.readShort());
												
												System.out.println("TTL = " + in.readByte());
												System.out.println("PROTOCOL SHOULD BE 0x1 = " + in.readByte());
												System.out.println("CHECKSUM = " + in.readShort());
												*/
												
												if (remaining < 4) {
													LOGGER.debug("Invalid packet (remaining {} bytes)", remaining);
													skip(in, remaining);
													continue;
												}
												String sourceIp = readIpV4(in);
												//%%%% System.out.println("sourceIp="+sourceIp);
												remaining -= 4;

												if (remaining < 4) {
													LOGGER.debug("Invalid packet (remaining {} bytes)", remaining);
													skip(in, remaining);
													continue;
												}
												String destinationIp = readIpV4(in);
												//%%%% System.out.println("destinationIp="+destinationIp);
												remaining -= 4;
												
												if (remaining < 2) {
													LOGGER.debug("Invalid packet (remaining {} bytes)", remaining);
													skip(in, remaining);
													continue;
												}
												int sourcePort = readShortBigEndian(in);
												//%%%% System.out.println("sourcePort="+sourcePort);
												remaining -= 2;

												if (remaining < 2) {
													LOGGER.debug("Invalid packet (remaining {} bytes)", remaining);
													skip(in, remaining);
													continue;
												}
												int destinationPort = readShortBigEndian(in);
												//%%%% System.out.println("destinationPort="+destinationPort);
												remaining -= 2;

												if (remaining < 2) {
													LOGGER.debug("Invalid packet (remaining {} bytes)", remaining);
													skip(in, remaining);
													continue;
												}
												int udpPacketSize = readShortBigEndian(in) - 8; // -8 because length includes udpPacketSize & checksum
												//%%%% System.out.println("udpPacketSize=" + udpPacketSize);
												remaining -= 2;

												if (remaining < 2) {
													LOGGER.debug("Invalid packet (remaining {} bytes)", remaining);
													skip(in, remaining);
													continue;
												}
												@SuppressWarnings("unused")
												int checksum = readShortBigEndian(in);
												//%%%% System.out.println("checksum=" + checksum);
												remaining -= 2;
												
												if ((udpPacketSize < 0) || (udpPacketSize > UDP_ERROR_LIMIT)) {
													LOGGER.debug("Invalid packet (remaining {} bytes)", remaining);
													skip(in, remaining);
													continue;
												}
												
												LOGGER.trace("Tcpdump loop, step 4");
												
												byte[] data = new byte[udpPacketSize];
												//%%%% System.out.println("READ " + udpPacketSize);
												if (remaining < data.length) {
													LOGGER.debug("Invalid packet (remaining {} bytes)", remaining);
													skip(in, remaining);
													continue;
												}
												in.readFully(data);
												remaining -= udpPacketSize;
												//%%%% System.out.println("SKIP remaining=" + remaining);
												
												LOGGER.trace("Tcpdump loop, step 5");

												skip(in, remaining);

												LOGGER.trace("Packet received: {}:{} -> {}:{} {}", sourceIp, sourcePort, destinationIp, destinationPort, DateUtils.from(timestamp));
												
												if (output != null) {
													output.writeInt(data.length);
													output.write(data);
													output.flush();
												}
												
												ReadyConnection connection = connections.get(new Address(destinationIp, destinationPort));
												if (connection != null) {
													connection.handle(new Address(sourceIp, sourcePort), ByteBuffer.wrap(data, 0, data.length));
													//%%% } else {
													//%%% System.out.println("NO CONNECTION " + connections.keySet());
												} else {
													LOGGER.debug("No match for packet: {}:{} -> {}:{} {}, available: {}", sourceIp, sourcePort, destinationIp, destinationPort, DateUtils.from(timestamp), connections.keySet());
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
		
		private void add(Address address, ReadyConnection connection) {
			connections.put(address, connection);
		}
		
		private void remove(Address address) {
			connections.remove(address);
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
		final Address receiveAddress;
		if (bind) {
			try {
				socket = new DatagramSocket();
			} catch (IOException ioe) {
				connection.failed(ioe);
				return;
			}
			receiveAddress = address;
		} else {
			try {
				socket = new DatagramSocket();
			} catch (IOException ioe) {
				connection.failed(ioe);
				return;
			}
			receiveAddress = new Address(address.getHost(), socket.getLocalPort());
		}
		
		receiver.add(receiveAddress, connection);

		connection.connected(new FailableCloseableByteBufferHandler() {
			private void closeSocket() {
				receiver.remove(receiveAddress);
				socket.close();
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
					a = address;
				}
				try {
					DatagramPacket packet = new DatagramPacket(buffer.array(), buffer.capacity(), InetAddress.getByName(a.getHost()), a.getPort());
					socket.send(packet);
				} catch (IOException ioe) {
					closeSocket();
					connection.failed(ioe);
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
