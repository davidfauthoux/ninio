package com.davfx.ninio.telnet.util;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.telnet.TelnetClientHandler;
import com.davfx.ninio.telnet.TelnetConnector;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class ScheduledCallingWrap implements TelnetConnector {
	private static final Config CONFIG = ConfigUtils.load(ScheduledCallingWrap.class);
	private final TelnetConnector wrappee;
	private final Queue queue;
	private final ScheduledExecutorService callWithEmptyExecutor;

	private double callWithEmptyTime = ConfigUtils.getDuration(CONFIG, "telnet.callWithEmptyTime");

	public ScheduledCallingWrap(Queue queue, ScheduledExecutorService callWithEmptyExecutor, TelnetConnector wrappee) {
		this.queue = queue;
		this.callWithEmptyExecutor = callWithEmptyExecutor;
		this.wrappee = wrappee;
	}
	
	public ScheduledCallingWrap withCallWithEmptyTime(double callWithEmptyTime) {
		this.callWithEmptyTime = callWithEmptyTime;
		return this;
	}

	@Override
	public TelnetConnector override(ReadyFactory readyFactory) {
		wrappee.override(readyFactory);
		return this;
	}
	@Override
	public TelnetConnector withAddress(Address address) {
		wrappee.withAddress(address);
		return this;
	}
	@Override
	public TelnetConnector withHost(String host) {
		wrappee.withHost(host);
		return this;
	}
	@Override
	public TelnetConnector withPort(int port) {
		wrappee.withPort(port);
		return this;
	}
	@Override
	public TelnetConnector withQueue(Queue queue) {
		wrappee.withQueue(queue);
		return this;
	}
	
	@Override
	public String getEol() {
		return wrappee.getEol();
	}
	
	@Override
	public void connect(final TelnetClientHandler clientHandler) {
		final Set<TelnetClientHandler> connections = new HashSet<>();
		if (callWithEmptyTime > 0d) {
			callWithEmptyExecutor.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					queue.post(new Runnable() {
						@Override
						public void run() {
							for (TelnetClientHandler r : connections) {
								r.received("");
							}
						}
					});
				}
			}, 0, (long) (callWithEmptyTime * 1000d), TimeUnit.MILLISECONDS);
		}

		wrappee.connect(new TelnetClientHandler() {
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
				clientHandler.launched(new TelnetClientHandler.Callback() {
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
