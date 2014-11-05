package com.davfx.ninio.common;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class SyncDatagramReady implements Ready {
	private static final Logger LOGGER = LoggerFactory.getLogger(SyncDatagramReady.class);
	private static final Config CONFIG = ConfigUtils.load(OnceByteBufferAllocator.class);
	private static final int BUFFER_SIZE = CONFIG.getBytes("ninio.sync.buffer.size").intValue();

	public static final class Receiver {
		private DatagramSocket socket = null;
		private final Object lock = new Object();
		private final Map<String, ReadyConnection> connections = new ConcurrentHashMap<>();
		public Receiver() {
			Executors.newSingleThreadExecutor().execute(new Runnable() {
				@Override
				public void run() {
					try {
						byte[] buffer = new byte[BUFFER_SIZE];
						synchronized (lock) {
							socket = new DatagramSocket();
							lock.notifyAll();
						}
						try {
							DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
							while (true) {
								socket.receive(packet);
								ReadyConnection connection = connections.get(key(packet.getAddress().getHostAddress(), packet.getPort()));
								if (connection != null) {
									connection.handle(null, ByteBuffer.wrap(buffer, packet.getOffset(), packet.getLength()));
								}
							}
						} finally {
							socket.close();
						}
					} catch (IOException ioe) {
						LOGGER.error("Could not open UDP receiver", ioe);
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
	public SyncDatagramReady(Receiver receiver) {
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
}
