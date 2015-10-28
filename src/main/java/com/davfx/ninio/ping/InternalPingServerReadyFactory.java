package com.davfx.ninio.ping;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ClassThreadFactory;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.util.ConfigUtils;
import com.google.common.base.Charsets;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Longs;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class InternalPingServerReadyFactory implements ReadyFactory, AutoCloseable {
	private static final Config CONFIG = ConfigFactory.load();
	private static final double TIMEOUT = ConfigUtils.getDuration(CONFIG, "ping.timeout");
	private static final int MAX_SIMULTANEOUS_CLIENTS = CONFIG.getInt("ping.maxSimultaneousClients");
	
	private final SyncPing syncPing;
	private final ExecutorService clientExecutor = Executors.newFixedThreadPool(MAX_SIMULTANEOUS_CLIENTS, new ClassThreadFactory(InternalPingServerReadyFactory.class));
	private final Map<String, List<PingCallback>> pinging = new HashMap<>();

	public InternalPingServerReadyFactory(SyncPing syncPing) {
		this.syncPing = syncPing;
	}
	
	@Override
	public void close() {
		clientExecutor.shutdown();
		synchronized (pinging) {
			pinging.clear();
		}
	}
	
	@Override
	public Ready create(final Queue queue) {
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
						final long id = bb.getLong();
						int l = bb.getInt();
						byte[] hostBytes = new byte[l];
						bb.get(hostBytes);
						final String host = new String(hostBytes, Charsets.UTF_8);
					
						ping(host, new PingCallback() {
							@Override
							public void handle(double elapsed) {
								if (closed) {
									return;
								}

								final ByteBuffer s = ByteBuffer.allocate(Longs.BYTES + Doubles.BYTES);
								s.putLong(id);
								s.putDouble(elapsed);
								s.flip();
						
								queue.post(new Runnable() {
									public void run() {
										connection.handle(null, s);
									}
								});
							}
						});
					}
				});
			}
		};
	}
	
	private static interface PingCallback {
		void handle(double elapsed);
	}
	private void ping(final String host, PingCallback callback) {
		if (clientExecutor.isShutdown()) {
			return;
		}
		
		boolean launch = false;
		synchronized (pinging) {
			List<PingCallback> callbacks = pinging.get(host);
			if (callbacks == null) {
				callbacks = new LinkedList<>();
				pinging.put(host, callbacks);
				launch = true;
			}
			callbacks.add(callback);
		}
		
		if (!launch) {
			return;
		}
		
		clientExecutor.execute(new Runnable() {
			@Override
			public void run() {
				long t = System.currentTimeMillis();
				boolean reachable = syncPing.isReachable(host, TIMEOUT);
				double elapsed = (System.currentTimeMillis() - t) / 1000d;
				if (!reachable) {
					elapsed = Double.NaN;
				}
				
				List<PingCallback> callbacks;
				synchronized (pinging) {
					callbacks = pinging.remove(host);
				}
				if (callbacks == null) {
					return;
				}
				for (PingCallback callback : callbacks) {
					callback.handle(elapsed);
				}
			}
		});
	}
}
