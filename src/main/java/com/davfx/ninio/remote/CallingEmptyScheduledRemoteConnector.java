package com.davfx.ninio.remote;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.davfx.ninio.common.Queue;

public final class CallingEmptyScheduledRemoteConnector implements RemoteConnector {
	private final Queue queue;
	private final RemoteConnector wrappee;
	private final WaitingRemoteClientConfigurator configurator;
	private volatile boolean closed = false;

	public CallingEmptyScheduledRemoteConnector(WaitingRemoteClientConfigurator configurator, Queue queue, RemoteConnector wrappee) {
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
		final Set<RemoteClientHandler> connections = new HashSet<>();
		if (configurator.callWithEmptyTime > 0d) {
			configurator.callWithEmptyExecutor.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					if (closed) {
						throw new RuntimeException("Stop requested");
					}
					
					queue.post(new Runnable() {
						@Override
						public void run() {
							for (RemoteClientHandler r : connections) {
								r.received("");
							}
						}
					});
				}
			}, 0, (long) (configurator.callWithEmptyTime * 1000d), TimeUnit.MILLISECONDS);
		}

		wrappee.connect(new RemoteClientHandler() {
			@Override
			public void failed(IOException e) {
				connections.remove(clientHandler);
				clientHandler.failed(e);
			}
			
			@Override
			public void close() {
				connections.remove(clientHandler);
				clientHandler.close();
			}
			
			@Override
			public void received(String text) {
				clientHandler.received(text);
			}
			
			@Override
			public void launched(final Callback callback) {
				connections.add(clientHandler);
				clientHandler.launched(new RemoteClientHandler.Callback() {
					@Override
					public void close() {
						connections.remove(clientHandler);
						callback.close();
					}
					
					@Override
					public void send(String line) {
						callback.send(line);
					}
				});
			}
		});
	}
}
