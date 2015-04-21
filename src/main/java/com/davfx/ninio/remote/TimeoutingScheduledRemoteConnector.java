package com.davfx.ninio.remote;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.davfx.ninio.common.Queue;
import com.davfx.util.Mutable;

public final class TimeoutingScheduledRemoteConnector implements RemoteConnector {
	private final Queue queue;
	private final RemoteConnector wrappee;
	private final WaitingRemoteClientConfigurator configurator;
	private volatile boolean closed = false;

	public TimeoutingScheduledRemoteConnector(WaitingRemoteClientConfigurator configurator, Queue queue, RemoteConnector wrappee) {
		this.configurator = configurator;
		this.wrappee = wrappee;
		this.queue = queue;
	}
	
	@Override
	public String getEol() {
		return wrappee.getEol();
	}

	@Override
	public void close() {
		closed = true;
		wrappee.close();
	}
	
	@Override
	public void connect(final RemoteClientHandler clientHandler) {
		final Mutable<Boolean> connected = new Mutable<Boolean>(false);
		if (configurator.connectTimeout > 0d) {
			configurator.connectTimeoutExecutor.schedule(new Runnable() {
				@Override
				public void run() {
					if (closed) {
						throw new RuntimeException("Stop requested");
					}
					
					queue.post(new Runnable() {
						@Override
						public void run() {
							if (connected.get() == null) {
								return;
							}
							if (connected.get()) {
								return;
							}
							clientHandler.failed(new IOException("Connect timeout"));
							connected.set(null);
						}
					});
				}
			}, (long) (configurator.connectTimeout * 1000d), TimeUnit.MILLISECONDS);
		}

		wrappee.connect(new RemoteClientHandler() {
			@Override
			public void failed(IOException e) {
				if (connected.get() == null) {
					return;
				}
				
				connected.set(null);
				clientHandler.failed(e);
			}
			
			@Override
			public void close() {
				if (connected.get() == null) {
					return;
				}
				
				connected.set(null);
				clientHandler.close();
			}
			
			@Override
			public void received(String text) {
				if (connected.get() == null) {
					return;
				}
				
				clientHandler.received(text);
			}
			
			@Override
			public void launched(Callback callback) {
				if (connected.get() == null) {
					callback.close();
					return;
				}
				
				connected.set(true);
				clientHandler.launched(callback);
			}
		});
	}
}
