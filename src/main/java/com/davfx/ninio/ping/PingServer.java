package com.davfx.ninio.ping;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ConfigUtils;
import com.davfx.util.CrcUtils;
import com.typesafe.config.Config;

// Start as sudo if you want to make it work with ICMP
// Use PingClient.override(new DatagramReadyFactory())
public final class PingServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(PingServer.class);
	private static final Config CONFIG = ConfigUtils.load(PingServer.class);

	public static void main(String[] args) {
		new PingServer(CONFIG.getInt("ping.port"), CONFIG.getInt("ping.maxSimultaneousClients"), new PureJavaSyncPing());
	}
	
	private static final int ERROR_CODE = 1;
	private static final int BUFFER_SIZE = 1024;
	
	private final SyncPing syncPing;
	private final int port;
	private final ExecutorService clientExecutor;
	
	public PingServer(int port, int maxNumberOfSimultaneousClients, SyncPing syncPing) {
		this.port = port;
		this.syncPing = syncPing;
		clientExecutor = Executors.newFixedThreadPool(maxNumberOfSimultaneousClients);
	}
	
	public void start() {
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			@Override
			public void run() {
				try (DatagramSocket ss = new DatagramSocket(port)) {
					byte[] receiveData = new byte[BUFFER_SIZE];
					while (true) {
						DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
						ss.receive(receivePacket);
						final byte[] b = new byte[receivePacket.getLength()];
						System.arraycopy(receivePacket.getData(), receivePacket.getOffset(), b, 0, b.length);
						final InetAddress sourceAddress = receivePacket.getAddress();
						final int sourcePort = receivePacket.getPort();
						LOGGER.trace("Packet received: " + b.length + " bytes from: " + sourceAddress + ":" + sourcePort);
						clientExecutor.execute(new Runnable() {
							@Override
							public void run() {
								ByteBuffer bb = ByteBuffer.wrap(b);
								
								int version = bb.get();
								int messageType = bb.get();
								if (messageType != 1) {
									LOGGER.warn("Invalid message type: {}", messageType);
									return;
								}
								int ipVersion = bb.get();
								int computedCrc = CrcUtils.crc16(ByteBuffer.wrap(b), bb.position());
								short crc = bb.getShort();
								if (crc != computedCrc) {
									LOGGER.warn("Invalid CRC: {}, should be: {}", crc, computedCrc);
									return;
								}
								byte[] ip = new byte[(ipVersion == 4) ? 4 : 16];
								bb.get(ip);
								int numberOfRetries = bb.getInt();
								double timeBetweenRetries = bb.getInt() / 1000d;
								double retryTimeout = bb.getInt() / 1000d;
								
								int[] statuses = new int[numberOfRetries];
								double[] times = new double[numberOfRetries];
							
								int k = 0;
								for (int i = 0; i < numberOfRetries; i++) {
									long t = System.currentTimeMillis();
									boolean reachable = syncPing.isReachable(ip, retryTimeout);
									double elapsed = (System.currentTimeMillis() - t) / 1000d;
									times[k] = elapsed;
									if (reachable) {
										statuses[k] = 0;
									} else {
										statuses[k] = ERROR_CODE;
									}
									k++;
	
									if (reachable) {
										break;
									}
	
									if (timeBetweenRetries > 0d) {
										try {
											Thread.sleep((long) (timeBetweenRetries * 1000d));
										} catch (InterruptedException e) {
										}
									}
								}
	
								byte[] sent = new byte[1 + 1 + 1 + 2 + ip.length + 4 + (k * 4)];
								ByteBuffer s = ByteBuffer.wrap(sent);
								s.put((byte) version);
								s.put((byte) 2); // Message type
								s.put((byte) ipVersion);
								int crcPos = s.position();
								s.putShort((short) 0); // CRC
								s.put(ip);
								s.putInt(k);
								for (int i = 0; i < k; i++) {
									if (statuses[i] == 0) {
										s.putInt((int) (times[i] * 1000d));
									} else {
										s.putInt(-ERROR_CODE);
									}
								}
								s.position(crcPos);
								short sentCrc = CrcUtils.crc16(ByteBuffer.wrap(sent), crcPos);
								s.putShort(sentCrc);
								
								try {
									ss.send(new DatagramPacket(sent, sent.length, sourceAddress, sourcePort));
								} catch (IOException ioe) {
									// Ignored
								}
							}
						});
					}
				} catch (IOException ioe) {
					LOGGER.error("Could not open server socket", ioe);
				}
			}
		});
	}
}
