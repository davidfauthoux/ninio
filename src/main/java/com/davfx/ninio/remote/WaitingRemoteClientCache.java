package com.davfx.ninio.remote;

import java.io.IOException;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.util.ConfigUtils;
import com.davfx.util.Pair;

public final class WaitingRemoteClientCache implements AutoCloseable {
	private static final int CACHE_EXPIRE_THRESHOLD = ConfigUtils.load(WaitingRemoteClientCache.class).getInt("remote.cache.expire.threshold");

	private final WaitingRemoteClientConfigurator configurator;
	private final Queue queue;

	private static final class Hold {
		public final WaitingRemoteClient client;
		public final List<WaitingRemoteClientHandler> handlers = new LinkedList<>();
		public final Deque<Pair<String, WaitingRemoteClientHandler.Callback.SendCallback>> toSend = new LinkedList<>();
		public WaitingRemoteClientHandler.Callback launchedCallback;

		public final StringBuilder initResponses = new StringBuilder();
		public String ready = null;
		
		public Hold(WaitingRemoteClient client) {
			this.client = client;
		}
	}
	
	private final Map<Address, Hold> clients = new HashMap<>();
	private final RemoteConnectorFactory remoteConnectorFactory;
	
	public WaitingRemoteClientCache(WaitingRemoteClientConfigurator configurator, Queue queue, RemoteConnectorFactory remoteConnectorFactory) {
		this.configurator = configurator;
		this.queue = queue;
		this.remoteConnectorFactory = remoteConnectorFactory;
	}

	public static interface Connectable {
		Connectable init(String command);
		void connect(WaitingRemoteClientHandler clientHandler);
	}
	
	public Connectable get(final Address address) {
		return new Connectable() {
			private final List<String> initCommands = new LinkedList<String>();
			@Override
			public Connectable init(String command) {
				initCommands.add(command);
				return this;
			}
			
			@Override
			public void connect(final WaitingRemoteClientHandler clientHandler) {
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
					c = new Hold(new WaitingRemoteClient(configurator, new CallingEmptyScheduledRemoteConnector(configurator, queue, remoteConnectorFactory.create(address))));
					
					final Hold cc = c;
					clients.put(address, cc);
					c.handlers.add(clientHandler);
					
					final WaitingRemoteClientHandler.Callback.SendCallback continueToSendCallback = new WaitingRemoteClientHandler.Callback.SendCallback() {
						@Override
						public void failed(IOException e) {
							clients.remove(address);
							for (WaitingRemoteClientHandler h : cc.handlers) {
								h.failed(e);
							}
							cc.client.close();
						}
						@Override
						public void received(String text) {
							Pair<String, WaitingRemoteClientHandler.Callback.SendCallback> current = cc.toSend.removeFirst();
							
							if (!cc.toSend.isEmpty()) {
								Pair<String, WaitingRemoteClientHandler.Callback.SendCallback> next = cc.toSend.getFirst();
								cc.launchedCallback.send(next.first, this);
							}
							
							current.second.received(text);
						}
					};
					
					final Runnable readyRunnable = new Runnable() {
						@Override
						public void run() {
							cc.ready = cc.initResponses.toString();
							for (WaitingRemoteClientHandler h : cc.handlers) {
								h.launched(cc.ready, new WaitingRemoteClientHandler.Callback() {
									@Override
									public void close() {
										// Connection never actually closed
										cc.handlers.remove(clientHandler);
									}
									@Override
									public void send(String line, SendCallback sendCallback) {
										boolean send = cc.toSend.isEmpty();
										
										cc.toSend.add(new Pair<String, WaitingRemoteClientHandler.Callback.SendCallback>(line, sendCallback));
										
										if (send) {
											cc.launchedCallback.send(line, continueToSendCallback);
										}
									}
								});
							}
						}
					};
					
					WaitingRemoteClientHandler.Callback.SendCallback globalInitCallback = new WaitingRemoteClientHandler.Callback.SendCallback() {
						@Override
						public void received(String text) {
							cc.initResponses.append(text);
							
							cc.toSend.removeFirst();
							
							if (!cc.toSend.isEmpty()) {
								Pair<String, WaitingRemoteClientHandler.Callback.SendCallback> next = cc.toSend.getFirst();
								cc.launchedCallback.send(next.first, this);
							} else {
								readyRunnable.run();
							}
						}
						@Override
						public void failed(IOException e) {
							clients.remove(address);
							for (WaitingRemoteClientHandler h : cc.handlers) {
								h.failed(e);
							}
							cc.client.close();
						}
					};
					Iterator<String> ii = initCommands.iterator();
					while (ii.hasNext()) {
						String initCommand = ii.next();
						cc.toSend.add(new Pair<String, WaitingRemoteClientHandler.Callback.SendCallback>(initCommand, globalInitCallback));
					}
					
					cc.client.connect(new WaitingRemoteClientHandler() {
						@Override
						public void failed(IOException e) {
							clients.remove(address);
							for (WaitingRemoteClientHandler h : cc.handlers) {
								h.failed(e);
							}
							cc.client.close();
						}
						@Override
						public void close() {
							clients.remove(address);
							for (WaitingRemoteClientHandler h : cc.handlers) {
								h.close();
							}
							cc.client.close();
						}
			
						@Override
						public void launched(String init, Callback callback) {
							cc.initResponses.append(init);
							cc.launchedCallback = callback;
							if (cc.toSend.isEmpty()) {
								readyRunnable.run();
							} else {
								Pair<String, WaitingRemoteClientHandler.Callback.SendCallback> p = cc.toSend.getFirst();
								cc.launchedCallback.send(p.first, p.second);
							}
						}
					});
				} else {
					final Hold cc = c;
					
					cc.handlers.add(clientHandler);
					if (cc.ready != null) {
						final WaitingRemoteClientHandler.Callback.SendCallback continueToSendCallback = new WaitingRemoteClientHandler.Callback.SendCallback() {
							@Override
							public void failed(IOException e) {
								clients.remove(address);
								for (WaitingRemoteClientHandler h : cc.handlers) {
									h.failed(e);
								}
								cc.client.close();
							}
							@Override
							public void received(String text) {
								Pair<String, WaitingRemoteClientHandler.Callback.SendCallback> current = cc.toSend.removeFirst();
								
								if (!cc.toSend.isEmpty()) {
									Pair<String, WaitingRemoteClientHandler.Callback.SendCallback> next = cc.toSend.getFirst();
									cc.launchedCallback.send(next.first, this);
								}

								current.second.received(text);
							}
						};
						
						clientHandler.launched(cc.ready, new WaitingRemoteClientHandler.Callback() {
							@Override
							public void close() {
								// Connection never actually closed
								cc.handlers.remove(clientHandler);
							}
							@Override
							public void send(String line, SendCallback sendCallback) {
								boolean send = cc.toSend.isEmpty();
								
								cc.toSend.add(new Pair<String, WaitingRemoteClientHandler.Callback.SendCallback>(line, sendCallback));
								
								if (send) {
									cc.launchedCallback.send(line, continueToSendCallback);
								}
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
