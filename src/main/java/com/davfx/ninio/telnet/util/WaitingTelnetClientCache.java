package com.davfx.ninio.telnet.util;

import java.io.IOException;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.telnet.TelnetClient;
import com.davfx.ninio.telnet.TelnetConnector;
import com.davfx.util.Pair;

public final class WaitingTelnetClientCache implements AutoCloseable {
	private final Queue queue;
	private final ScheduledExecutorService callWithEmptyExecutor = Executors.newSingleThreadScheduledExecutor();
	
	private static final class Hold {
		public final WaitingTelnetClient client;
		public final List<WaitingTelnetClientHandler> handlers = new LinkedList<>();
		public final Deque<Pair<String, WaitingTelnetClientHandler.Callback.SendCallback>> toSend = new LinkedList<>();
		public WaitingTelnetClientHandler.Callback launchedCallback;

		public final StringBuilder initResponses = new StringBuilder();
		public String ready = null;
		
		public Hold(WaitingTelnetClient client) {
			this.client = client;
		}
	}
	
	private final Map<Address, Hold> clients = new HashMap<>();

	private double callWithEmptyTime = Double.NaN;
	private double endOfCommandTime = Double.NaN;
	private double timeout = Double.NaN;
	private ReadyFactory readyFactory = null;
	private TelnetConnectorFactory telnetConnectorFactory = new TelnetConnectorFactory() {
		@Override
		public TelnetConnector create(Address address) {
			return new TelnetClient();
		}
	};
	
	public WaitingTelnetClientCache(Queue queue) {
		this.queue = queue;
	}

	public WaitingTelnetClientCache withCallWithEmptyTime(double callWithEmptyTime) {
		this.callWithEmptyTime = callWithEmptyTime;
		return this;
	}
	public WaitingTelnetClientCache withEndOfCommandTime(double endOfCommandTime) {
		this.endOfCommandTime = endOfCommandTime;
		return this;
	}
	public WaitingTelnetClientCache withTimeout(double timeout) {
		this.timeout = timeout;
		return this;
	}

	public WaitingTelnetClientCache override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	public WaitingTelnetClientCache override(TelnetConnectorFactory telnetConnectorFactory) {
		this.telnetConnectorFactory = telnetConnectorFactory;
		return this;
	}
	
	public static interface Connectable {
		Connectable init(String command);
		void connect(WaitingTelnetClientHandler clientHandler);
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
			public void connect(WaitingTelnetClientHandler clientHandler) {
				Hold c = clients.get(address);
				if (c == null) {
					ScheduledCallingWrap telnetClient = new ScheduledCallingWrap(queue, callWithEmptyExecutor, telnetConnectorFactory.create(address));
					if (!Double.isNaN(callWithEmptyTime)) {
						telnetClient.withCallWithEmptyTime(callWithEmptyTime);
					}
					if (readyFactory != null) {
						telnetClient.override(readyFactory);
					}
					
					WaitingTelnetClient waitingTelnetClient = new WaitingTelnetClient(telnetClient.withAddress(address).withQueue(queue));
					if (!Double.isNaN(endOfCommandTime)) {
						waitingTelnetClient.withEndOfCommandTime(endOfCommandTime);
					}
					if (!Double.isNaN(timeout)) {
						waitingTelnetClient.withTimeout(timeout);
					}
					
					c = new Hold(waitingTelnetClient);
					
					final Hold cc = c;
					clients.put(address, cc);
					c.handlers.add(clientHandler);
					
					final WaitingTelnetClientHandler.Callback.SendCallback continueToSendCallback = new WaitingTelnetClientHandler.Callback.SendCallback() {
						@Override
						public void failed(IOException e) {
							clients.remove(address);
							for (WaitingTelnetClientHandler h : cc.handlers) {
								h.failed(e);
							}
						}
						@Override
						public void received(String text) {
							Pair<String, WaitingTelnetClientHandler.Callback.SendCallback> current = cc.toSend.removeFirst();
							
							if (!cc.toSend.isEmpty()) {
								Pair<String, WaitingTelnetClientHandler.Callback.SendCallback> next = cc.toSend.getFirst();
								cc.launchedCallback.send(next.first, this);
							}
							
							current.second.received(text);
						}
					};
					
					final Runnable readyRunnable = new Runnable() {
						@Override
						public void run() {
							cc.ready = cc.initResponses.toString();
							for (WaitingTelnetClientHandler h : cc.handlers) {
								h.launched(cc.ready, new WaitingTelnetClientHandler.Callback() {
									@Override
									public void close() {
										// Never actually closed
									}
									@Override
									public void send(String line, SendCallback sendCallback) {
										boolean send = cc.toSend.isEmpty();
										
										cc.toSend.add(new Pair<String, WaitingTelnetClientHandler.Callback.SendCallback>(line, sendCallback));
										
										if (send) {
											cc.launchedCallback.send(line, continueToSendCallback);
										}
									}
								});
							}
						}
					};
					
					WaitingTelnetClientHandler.Callback.SendCallback globalInitCallback = new WaitingTelnetClientHandler.Callback.SendCallback() {
						@Override
						public void received(String text) {
							cc.initResponses.append(text);
							
							cc.toSend.removeFirst();
							
							if (!cc.toSend.isEmpty()) {
								Pair<String, WaitingTelnetClientHandler.Callback.SendCallback> next = cc.toSend.getFirst();
								cc.launchedCallback.send(next.first, this);
							} else {
								readyRunnable.run();
							}
						}
						@Override
						public void failed(IOException e) {
							clients.remove(address);
							for (WaitingTelnetClientHandler h : cc.handlers) {
								h.failed(e);
							}
						}
					};
					Iterator<String> ii = initCommands.iterator();
					while (ii.hasNext()) {
						String initCommand = ii.next();
						cc.toSend.add(new Pair<String, WaitingTelnetClientHandler.Callback.SendCallback>(initCommand, globalInitCallback));
					}
					
					cc.client.connect(new WaitingTelnetClientHandler() {
						@Override
						public void failed(IOException e) {
							clients.remove(address);
							for (WaitingTelnetClientHandler h : cc.handlers) {
								h.failed(e);
							}
						}
						@Override
						public void close() {
							clients.remove(address);
							for (WaitingTelnetClientHandler h : cc.handlers) {
								h.close();
							}
						}
			
						@Override
						public void launched(String init, Callback callback) {
							cc.initResponses.append(init);
							cc.launchedCallback = callback;
							if (cc.toSend.isEmpty()) {
								readyRunnable.run();
							} else {
								Pair<String, WaitingTelnetClientHandler.Callback.SendCallback> p = cc.toSend.getFirst();
								cc.launchedCallback.send(p.first, p.second);
							}
						}
					});
				} else {
					final Hold cc = c;
					
					cc.handlers.add(clientHandler);
					if (cc.ready != null) {
						final WaitingTelnetClientHandler.Callback.SendCallback continueToSendCallback = new WaitingTelnetClientHandler.Callback.SendCallback() {
							@Override
							public void failed(IOException e) {
								clients.remove(address);
								for (WaitingTelnetClientHandler h : cc.handlers) {
									h.failed(e);
								}
							}
							@Override
							public void received(String text) {
								Pair<String, WaitingTelnetClientHandler.Callback.SendCallback> current = cc.toSend.removeFirst();
								
								if (!cc.toSend.isEmpty()) {
									Pair<String, WaitingTelnetClientHandler.Callback.SendCallback> next = cc.toSend.getFirst();
									cc.launchedCallback.send(next.first, this);
								}

								current.second.received(text);
							}
						};
						
						clientHandler.launched(cc.ready, new WaitingTelnetClientHandler.Callback() {
							@Override
							public void close() {
								// Never actually closed
							}
							@Override
							public void send(String line, SendCallback sendCallback) {
								boolean send = cc.toSend.isEmpty();
								
								cc.toSend.add(new Pair<String, WaitingTelnetClientHandler.Callback.SendCallback>(line, sendCallback));
								
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
		callWithEmptyExecutor.shutdown();
		// Queue not closed here but by caller
		
		for(Hold c : clients.values()) {
			if (c.launchedCallback != null) {
				c.launchedCallback.close();
			}
		}
		clients.clear();
	}
}
