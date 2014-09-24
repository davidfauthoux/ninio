package com.davfx.ninio.ping.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.ping.PingClient;
import com.davfx.ninio.ping.PingClientHandler;
import com.davfx.ninio.ping.PingableAddress;

public final class PingClientCache implements AutoCloseable {
	private final Queue queue;
	private final ScheduledExecutorService repeatExecutor = Executors.newSingleThreadScheduledExecutor();
	
	private static final class Hold {
		public final PingClient client;
		public final List<PingClientHandler> handlers = new LinkedList<>();
		public PingClientHandler.Callback launchedCallback;
		public Hold(PingClient client) {
			this.client = client;
		}		
	}
	
	private final Map<Address, Hold> clients = new HashMap<>();

	private double minTimeToRepeat = Double.NaN;
	private double repeatTime = Double.NaN;
	private double timeoutFromBeginning = Double.NaN;
	private ReadyFactory readyFactory = null;
	
	public PingClientCache(Queue queue) {
		this.queue = queue;
	}

	public PingClientCache withMinTimeToRepeat(double minTimeToRepeat) {
		this.minTimeToRepeat = minTimeToRepeat;
		return this;
	}
	public PingClientCache withRepeatTime(double repeatTime) {
		this.repeatTime = repeatTime;
		return this;
	}

	public PingClientCache withTimeoutFromBeginning(double timeoutFromBeginning) {
		this.timeoutFromBeginning = timeoutFromBeginning;
		return this;
	}

	public PingClientCache override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	public static interface Connectable {
		void connect(PingClientHandler clientHandler);
	}
	
	public Connectable get(String host) {
		return get(new Address(host, PingClient.DEFAULT_PORT));
		
	}
	public Connectable get(final Address address) {
		return new Connectable() {
			@Override
			public void connect(PingClientHandler clientHandler) {
				Hold c = clients.get(address);
				if (c == null) {
					PingClient pingClient = new PingClient();
					if (!Double.isNaN(minTimeToRepeat)) {
						pingClient.withMinTimeToRepeat(minTimeToRepeat);
					}
					if (!Double.isNaN(repeatTime)) {
						pingClient.withRepeatTime(repeatTime);
					}
					if (!Double.isNaN(timeoutFromBeginning)) {
						pingClient.withTimeoutFromBeginning(timeoutFromBeginning);
					}
					if (readyFactory != null) {
						pingClient.override(readyFactory);
					}
					c = new Hold(pingClient.withAddress(address).withQueue(queue, repeatExecutor));
					
					final Hold cc = c;
					clients.put(address, cc);
					c.handlers.add(clientHandler);
					
					c.client.connect(new PingClientHandler() {
						@Override
						public void failed(IOException e) {
							clients.remove(address);
							for (PingClientHandler h : cc.handlers) {
								h.failed(e);
							}
						}
						@Override
						public void close() {
							clients.remove(address);
							for (PingClientHandler h : cc.handlers) {
								h.close();
							}
						}
						@Override
						public void launched(final Callback callback) {
							cc.launchedCallback = callback;
							for (PingClientHandler h : cc.handlers) {
								h.launched(new Callback() {
									@Override
									public void close() {
										// Never actually closed
									}
									@Override
									public void ping(PingableAddress address, int numberOfRetries, double timeBetweenRetries, double retryTimeout, PingCallback pingCallback) {
										callback.ping(address, numberOfRetries, timeBetweenRetries, retryTimeout, pingCallback);
									}
								});
							}
						}
					});
				} else {
					final Hold cc = c;
					
					cc.handlers.add(clientHandler);
					if (cc.launchedCallback != null) {
						clientHandler.launched(new PingClientHandler.Callback() {
							@Override
							public void close() {
								// Never actually closed
							}
							@Override
							public void ping(PingableAddress address, int numberOfRetries, double timeBetweenRetries, double retryTimeout, PingCallback pingCallback) {
								cc.launchedCallback.ping(address, numberOfRetries, timeBetweenRetries, retryTimeout, pingCallback);
							}
						});
					}
				}
			}
		};
	}
	
	@Override
	public void close() {
		repeatExecutor.shutdown();
		// Queue not closed here but by caller
		
		for(Hold c : clients.values()) {
			if (c.launchedCallback != null) {
				c.launchedCallback.close();
			}
			c.handlers.clear();
		}
	}
}
