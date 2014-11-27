package com.davfx.ninio.ping.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.ping.OldPingClient;
import com.davfx.ninio.ping.OldPingClientConfigurator;
import com.davfx.ninio.ping.OldPingClientHandler;
import com.davfx.ninio.ping.PingClientConfigurator;
import com.davfx.ninio.ping.PingableAddress;
import com.davfx.util.ConfigUtils;

@Deprecated
public final class OldPingClientCache implements AutoCloseable {
	private static final int CACHE_EXPIRE_THRESHOLD = ConfigUtils.load(OldPingClientCache.class).getInt("ping.cache.expire.threshold");

	private final OldPingClientConfigurator configurator;
	
	private static final class Hold {
		public final OldPingClient client;
		public final List<OldPingClientHandler> handlers = new LinkedList<>();
		public OldPingClientHandler.Callback launchedCallback;
		public Hold(OldPingClient client) {
			this.client = client;
		}		
	}
	
	private final Map<Address, Hold> clients = new HashMap<>();

	public OldPingClientCache(OldPingClientConfigurator configurator) {
		this.configurator = configurator;
	}

	public static interface Connectable {
		void connect(OldPingClientHandler clientHandler);
	}
	
	public Connectable get(String host) {
		return get(new Address(host, PingClientConfigurator.DEFAULT_PORT));
		
	}
	public Connectable get(final Address address) {
		return new Connectable() {
			@Override
			public void connect(final OldPingClientHandler clientHandler) {
				if ((CACHE_EXPIRE_THRESHOLD > 0) && (clients.size() >= CACHE_EXPIRE_THRESHOLD)) {
					Iterator<Hold> i = clients.values().iterator();
					while (i.hasNext()) {
						Hold c = i.next();
						if (c.handlers.isEmpty()) {
							if (c.launchedCallback != null) {
								c.launchedCallback.close();
							}
							i.remove();
							c.client.close();
						}
					}
				}
				
				Hold c = clients.get(address);
				if (c == null) {
					c = new Hold(new OldPingClient(new OldPingClientConfigurator(configurator).withAddress(address)));
					
					final Hold cc = c;
					clients.put(address, cc);
					c.handlers.add(clientHandler);
					
					c.client.connect(new OldPingClientHandler() {
						@Override
						public void failed(IOException e) {
							clients.remove(address);
							for (OldPingClientHandler h : cc.handlers) {
								h.failed(e);
							}
							cc.client.close();
						}
						@Override
						public void close() {
							clients.remove(address);
							for (OldPingClientHandler h : cc.handlers) {
								h.close();
							}
							cc.client.close();
						}
						@Override
						public void launched(final Callback callback) {
							cc.launchedCallback = callback;
							for (OldPingClientHandler h : cc.handlers) {
								h.launched(new Callback() {
									@Override
									public void close() {
										// Connection never actually closed
										cc.handlers.remove(clientHandler);
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
						clientHandler.launched(new OldPingClientHandler.Callback() {
							@Override
							public void close() {
								// Connection never actually closed
								cc.handlers.remove(clientHandler);
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
		for(Hold c : clients.values()) {
			if (c.launchedCallback != null) {
				c.launchedCallback.close();
			}
			c.client.close();
		}
		clients.clear();
	}
}
