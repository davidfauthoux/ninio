package com.davfx.ninio.ping;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;
import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;

public final class PingClient implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(PingClient.class);

	private final Queue queue;
	private final ReadyFactory readyFactory;

	private final RequestIdProvider requestIdProvider = new RequestIdProvider();
	private final InstanceMapper instanceMapper;

	public PingClient(Queue queue, ReadyFactory readyFactory) {
		this.queue = queue;
		this.readyFactory = readyFactory;
		instanceMapper = new InstanceMapper(requestIdProvider);
	}

	@Override
	public void close() {
		queue.post(new Runnable() {
			@Override
			public void run() {
				instanceMapper.closeAll();
			}
		});
	}
	
	private static final class RequestIdProvider {
		private long nextRequestId = 0L;

		public RequestIdProvider() {
		}
		
		public long get() {
			long id = nextRequestId;
			nextRequestId++;
			return id;
		}
	}
	
	public void connect(final PingClientHandler clientHandler) {
		queue.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = readyFactory.create();
				
				ready.connect(null, new ReadyConnection() {

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						long id = buffer.getLong();
						double time = buffer.getDouble();
						instanceMapper.handle(id, time);
					}
					
					@Override
					public void failed(IOException e) {
						clientHandler.failed(e);
					}
					
					@Override
					public void connected(final FailableCloseableByteBufferHandler write) {
						final PingWriter w = new PingWriter(write);
						
						clientHandler.launched(new PingClientHandler.Callback() {
							@Override
							public void close() {
								write.close();
							}
							@Override
							public void ping(String host, PingCallback callback) {
								Instance i = new Instance(callback, w);
								instanceMapper.map(i);
								w.ping(i.instanceId, host);
							}
						});
					}
					
					@Override
					public void close() {
						clientHandler.close();
					}
				});
			}
		});
	}
	
	private static final class InstanceMapper {
		private final Map<Long, Instance> instances = new HashMap<>();
		private RequestIdProvider requestIdProvider;
		
		public InstanceMapper(RequestIdProvider requestIdProvider) {
			this.requestIdProvider = requestIdProvider;
		}
		
		public void map(Instance instance) {
			long instanceId = requestIdProvider.get();

			if (instances.containsKey(instanceId)) {
				LOGGER.warn("The maximum number of simultaneous request has been reached");
				return;
			}
			
			instances.put(instanceId, instance);
			
			instance.instanceId = instanceId;
		}

		public void closeAll() {
			for (Instance i : instances.values()) {
				i.closeAll();
			}
			instances.clear();
		}
	
		public void handle(long instanceId, double time) {
			Instance i = instances.remove(instanceId);
			if (i == null) {
				return;
			}
			i.handle(time);
		}
	}
	
	private static final class PingWriter {
		private final CloseableByteBufferHandler write;
		public PingWriter(CloseableByteBufferHandler write) {
			this.write = write;
		}
		public void close() {
			write.close();
		}
		public void ping(long instanceId, String host) {
			byte[] h = host.getBytes(Charsets.UTF_8);
			ByteBuffer bb = ByteBuffer.allocate(Longs.BYTES + Ints.BYTES + h.length);
			bb.putLong(instanceId);
			bb.putInt(h.length);
			bb.put(h);
			bb.flip();
			write.handle(null, bb);
		}
	}
	
	private static final class Instance {
		private final PingWriter write;
		private PingClientHandler.Callback.PingCallback callback;
		public long instanceId;

		public Instance(PingClientHandler.Callback.PingCallback callback, PingWriter write) {
			this.callback = callback;
			this.write = write;
		}
		
		public void closeAll() {
			write.close();
			
			if (callback == null) {
				return;
			}
			
			//%% PingClientHandler.Callback.PingCallback c = callback;
			callback = null;
			//%% c.failed(new IOException("Closed by peer"));
		}
		
		private void handle(double time) {
			PingClientHandler.Callback.PingCallback c = callback;
			callback = null;
			c.pong(time);
		}
	}
}
