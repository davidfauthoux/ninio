package com.davfx.ninio.ping;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.util.CrcUtils;
import com.davfx.util.WaitUtils;

public final class InternalPingServerReadyFactory implements ReadyFactory, AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(InternalPingServerReadyFactory.class);

	private static final int ERROR_CODE = 1;
	private final ExecutorService clientExecutor;

	public InternalPingServerReadyFactory(int maxNumberOfSimultaneousClients) {
		clientExecutor = Executors.newFixedThreadPool(maxNumberOfSimultaneousClients);
	}
	
	@Override
	public void close() {
		clientExecutor.shutdown();
	}
	
	@Override
	public Ready create(Queue queue) {
		return new Ready() {
			@Override
			public void connect(Address address, final ReadyConnection connection) {
				connection.connected(new FailableCloseableByteBufferHandler() {
					private volatile boolean closed = false;
					
					@Override
					public void close() {
						closed = true;
					}
					
					@Override
					public void failed(IOException e) {
						close();
					}
					
					@Override
					public void handle(Address address, ByteBuffer bb) {
						ByteBuffer b = bb.duplicate();
						final int version = bb.get();
						int messageType = bb.get();
						if (messageType != 1) {
							LOGGER.warn("Invalid message type: {}", messageType);
							return;
						}
						final int ipVersion = bb.get();
						int computedCrc = CrcUtils.crc16(b, bb.position());
						short crc = bb.getShort();
						if (crc != computedCrc) {
							LOGGER.warn("Invalid CRC: {}, should be: {}", crc, computedCrc);
							return;
						}
						final byte[] ip = new byte[(ipVersion == 4) ? 4 : 16];
						bb.get(ip);
						final int numberOfRetries = bb.getInt();
						final double timeBetweenRetries = bb.getInt() / 1000d;
						final double retryTimeout = bb.getInt() / 1000d;
					
						clientExecutor.execute(new Runnable() {
							@Override
							public void run() {
								int[] statuses = new int[numberOfRetries];
								double[] times = new double[numberOfRetries];

								int k = 0;
								for (int i = 0; i < numberOfRetries; i++) {
									long t = System.currentTimeMillis();
									boolean reachable;
									InetAddress toReach;
									try {
										toReach = InetAddress.getByAddress(ip);
									} catch (IOException ioe) {
										toReach = null;
										reachable = false;
									}
									if (toReach != null) {
										try {
											reachable = toReach.isReachable((int) (retryTimeout * 1000d));
										} catch (IOException ioe) {
											LOGGER.debug("Unreachable", ioe);
											reachable = false;
										}
										LOGGER.trace("{} reachable? {}", toReach, reachable);
									} else {
										reachable = false;
									}
									double elapsed = (System.currentTimeMillis() - t) / 1000d;
									times[k] = elapsed;
									if (reachable) {
										statuses[k] = 0;
									} else {
										statuses[k] = ERROR_CODE;
									}
									k++;
			
									if (closed || clientExecutor.isShutdown()) {
										return;
									}

									if (reachable) {
										break;
									}
			
									if (timeBetweenRetries > 0d) {
										WaitUtils.wait(timeBetweenRetries);
									}

									if (closed || clientExecutor.isShutdown()) {
										return;
									}
								}
								
								final ByteBuffer s = ByteBuffer.allocate(1 + 1 + 1 + 2 + ip.length + 4 + (k * 4));
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
								s.flip();
								int p = s.position();
			
								ByteBuffer ss = s.duplicate();
								s.position(crcPos);
								short sentCrc = CrcUtils.crc16(ss, crcPos);
								s.putShort(sentCrc);
								s.position(p);
						
								if (closed || clientExecutor.isShutdown()) {
									return;
								}
								connection.handle(null, s);
							}
						});
					}
				});
			}
		};
	}
}
