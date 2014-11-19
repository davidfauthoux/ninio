package com.davfx.ninio.ping;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.util.CrcUtils;
import com.davfx.util.DateUtils;

public final class PingClient implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(PingClient.class);

	private final PingClientConfigurator configurator;
	private final Set<InstanceMapper> instanceMappers = new HashSet<>();
	private volatile boolean closed = false;

	public PingClient(final PingClientConfigurator configurator) {
		this.configurator = configurator;
		
		configurator.repeatExecutor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				if (closed) {
					throw new RuntimeException("Normal closed");
				}
				
				configurator.queue.post(new Runnable() {
					@Override
					public void run() {
						Date now = new Date();
						for (InstanceMapper i : instanceMappers) {
							i.repeat(now, configurator.minTimeToRepeat, configurator.timeoutFromBeginning);
						}
					}
				});
			}
		}, 0, (long) (configurator.repeatTime * 1000d), TimeUnit.MILLISECONDS);
	}
	
	@Override
	public void close() {
		closed = true;
	}
	
	public void connect(final PingClientHandler clientHandler) {
		configurator.queue.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = configurator.readyFactory.create(configurator.queue);
				
				final InstanceMapper instanceMapper = new InstanceMapper();
				instanceMappers.add(instanceMapper);

				ready.connect(configurator.address, new ReadyConnection() {

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						ByteBuffer bb = buffer.duplicate();

						int version = buffer.get() & 0xFF;
						if (version != 1) {
							LOGGER.warn("Invalid version: {}", version);
							return;
						}
						int messageType = buffer.get() & 0xFF;
						if (messageType != 2) {
							LOGGER.warn("Invalid message type: {}", messageType);
							return;
						}
						int ipVersion = buffer.get() & 0xFF;
						int crcPos = buffer.position();
						short crc = buffer.getShort();
						if (CrcUtils.crc16(bb, crcPos) != crc) {
							LOGGER.warn("Invalid crc");
							return;
						}
						byte[] ip = new byte[(ipVersion == 4) ? 4 : 16];
						buffer.get(ip);
						int numberOfRetries = buffer.getInt();
						int[] statuses = new int[numberOfRetries];
						double[] times = new double[numberOfRetries];
						for (int i = 0; i < numberOfRetries; i++) {
							int replyTime = buffer.getInt();
							if (replyTime < 0) {
								// Error
								statuses[i] = -replyTime;
								times[i] = Double.NaN;
							} else {
								times[i] = replyTime / 1000d;
								statuses[i] = PingClientHandler.VALID_STATUS;
							}
						}

						instanceMapper.handle(new PingableAddress(ip), statuses, times);
					}
					
					@Override
					public void failed(IOException e) {
						if (instanceMappers.remove(instanceMapper)) {
							clientHandler.failed(e);
						}
					}
					
					@Override
					public void connected(final FailableCloseableByteBufferHandler write) {
						final PingWriter w = new PingWriter(write);
						
						clientHandler.launched(new PingClientHandler.Callback() {
							@Override
							public void close() {
								if (instanceMappers.remove(instanceMapper)) {
									instanceMapper.closedByUser();
								}
								
								write.close();
							}
							@Override
							public void ping(PingableAddress address, int numberOfRetries, double timeBetweenRetries, double retryTimeout, PingCallback callback) {
								Instance i = new Instance(callback, w, address, numberOfRetries, timeBetweenRetries, retryTimeout);
								instanceMapper.map(i);
								w.ping(address, numberOfRetries, timeBetweenRetries, retryTimeout);
							}
						});
					}
					
					@Override
					public void close() {
						if (instanceMappers.remove(instanceMapper)) {
							instanceMapper.closedByPeer();
						}
					}
				});
			}
		});
	}
	
	private static final class InstanceMapper {
		private final Map<String, List<Instance>> instances = new HashMap<>();
		
		public InstanceMapper() {
		}
		
		public void map(Instance instance) {
			// We should have an ID on the server side, but hey, it has been done like that by Remi, do you remember Remi??
			String key = instance.pingedAddress.toString() + "/" + String.valueOf(instance.numberOfRetries); 
			List<Instance> l = instances.get(key);
			if (l == null) {
				l = new LinkedList<>();
				instances.put(key, l);
			}
			l.add(instance);
		}
		
		public void closedByUser() {
			instances.clear();
		}
		public void closedByPeer() {
			for (List<Instance> l : instances.values()) {
				for (Instance i : l) {
					i.closedByPeer();
				}
			}
			instances.clear();
		}
		
		public void handle(PingableAddress a, int[] statuses, double[] times) {
			String key = a.toString() + "/" + String.valueOf(statuses.length); 
			List<Instance> l = instances.remove(key);
			if (l == null) {
				return;
			}
			for (Instance i : l) {
				i.handle(a, statuses, times);
			}
		}
		
		public boolean repeat(Date now, double minTimeToRepeat, double timeoutFromBeginning) {
			for (List<Instance> l : instances.values()) {
				for (Instance i : l) {
					i.repeat(now, minTimeToRepeat, timeoutFromBeginning);
				}
			}
			
			Iterator<List<Instance>> li = instances.values().iterator();
			while (li.hasNext()) {
				List<Instance> l = li.next();
				Iterator<Instance> ii = l.iterator();
				while (ii.hasNext()) {
					Instance i = ii.next();
					if (i.callback == null) {
						ii.remove();
					}
				}
				if (l.isEmpty()) {
					li.remove();
				}
			}
			return !instances.isEmpty();
		}
	}
	
	private static final class PingWriter {
		private final CloseableByteBufferHandler write;
		public PingWriter(CloseableByteBufferHandler write) {
			this.write = write;
		}
		public void ping(PingableAddress a, int numberOfRetries, double timeBetweenRetries, double retryTimeout) {
			byte[] b = new byte[1 + 1 + 1 + 2 + a.ip.length + 4 + 4 + 4];
			ByteBuffer bb = ByteBuffer.wrap(b);
			bb.put((byte) 1); // Version
			bb.put((byte) 1); // Message Type (request)
			bb.put((byte) ((a.ip.length == 4) ? 4 : 6)); // IP Version

			int crcPos = bb.position();
			bb.position(crcPos + 2);

			bb.put(a.ip);
			bb.putInt(numberOfRetries);
			bb.putInt((int) (timeBetweenRetries * 1000d));
			bb.putInt((int) (retryTimeout * 1000d));

			int pos = bb.position();
			bb.position(crcPos);
			bb.putShort(CrcUtils.crc16(ByteBuffer.wrap(b), crcPos));
			bb.position(pos);

			bb.flip();

			write.handle(null, bb);
		}
	}
	
	private static final class Instance {
		private PingClientHandler.Callback.PingCallback callback;
		private final PingWriter write;
		private final PingableAddress pingedAddress;
		private final int numberOfRetries;
		private final double timeBetweenRetries;
		private final double retryTimeout;
		private final Date beginningTimestamp = new Date();
		private Date sendTimestamp = new Date();

		public Instance(PingClientHandler.Callback.PingCallback callback, PingWriter write, PingableAddress pingedAddress, int numberOfRetries, double timeBetweenRetries, double retryTimeout) {
			this.callback = callback;
			this.write = write;
			this.pingedAddress = pingedAddress;
			this.numberOfRetries = numberOfRetries;
			this.timeBetweenRetries = timeBetweenRetries;
			this.retryTimeout = retryTimeout;
		}
		
		public void closedByPeer() {
			if (callback == null) {
				return;
			}
			
			PingClientHandler.Callback.PingCallback c = callback;
			callback = null;
			c.failed(new IOException("Closed by peer"));
		}
		
		public void repeat(Date now, double minTimeToRepeat, double timeoutFromBeginning) {
			if (callback == null) {
				return;
			}

			double n = DateUtils.from(now);
			
			if ((n - DateUtils.from(beginningTimestamp)) >= timeoutFromBeginning) {
				PingClientHandler.Callback.PingCallback c = callback;
				callback = null;
				c.failed(new IOException("Timeout"));
				return;
			}

			if ((n - DateUtils.from(sendTimestamp)) >= minTimeToRepeat) {
				write.ping(pingedAddress, numberOfRetries, timeBetweenRetries, retryTimeout);
			}
		}
		
		private void handle(PingableAddress a, int[] statuses, double[] times) {
			PingClientHandler.Callback.PingCallback c = callback;
			callback = null;
			c.pong(statuses, times);
		}
	}
}
