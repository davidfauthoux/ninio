package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class SocketServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(SocketServer.class);

	private static final Config CONFIG = ConfigFactory.load(SocketServer.class.getClassLoader());
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.read.size").intValue();
	// private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.write.size").intValue();
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.write.buffer").longValue();
	// private static final double TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.socket.timeout");

	private final Set<SocketChannel> outboundChannels = new HashSet<>();
	
	//%% private final InternalQueue queue;

	private Executor executor = Shared.EXECUTOR;
	
	private Acceptable acceptable = null;

	public SocketServer() {
	}
	
	/*%%
	public SocketServer(InternalQueue queue) {
		this.queue = queue;
	}
	*/
	
	public SocketServer with(Executor executor) {
		this.executor = executor;
		return this;
	}

	private void removed(final SocketChannel outboundChannel) {
		InternalQueue.execute(new Runnable() {
			@Override
			public void run() {
				try {
					outboundChannel.close();
				} catch (IOException ee) {
				}
				outboundChannels.remove(outboundChannel);
				LOGGER.debug("<- Clients connected: {}", outboundChannels.size());
			}
		});
	}
	
	public Acceptable create(final Address bindAddress) {
		Acceptable c = acceptable;
		if (c != null) {
			c.close();
		}
		
		final Executor thisExecutor = executor;
		
		acceptable = new Acceptable() {
			private ServerSocketChannel currentChannel = null;
			private SelectionKey currentAcceptSelectionKey = null;

			private Accepting accepting = null;
			private Failing failing = null;
			
			@Override
			public Acceptable accepting(Accepting accepting) {
				this.accepting = accepting;
				return this;
			}
			
			@Override
			public Acceptable failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public Acceptable accept(final ListenConnectingable listening) {
				final Accepting thisAccepting = accepting;
				final Failing thisFailing = failing;
				
				InternalQueue.execute(new Runnable() {
					@Override
					public void run() {
						if (currentChannel != null) {
							throw new IllegalStateException();
						}
						if (currentAcceptSelectionKey != null) {
							throw new IllegalStateException();
						}
						
						try {
							final ServerSocketChannel serverChannel = ServerSocketChannel.open();
							currentChannel = serverChannel;
							try {
								serverChannel.configureBlocking(false);
								// serverChannel.socket().setReceiveBufferSize();
								
								LOGGER.debug("-> Server channel ready to accept on: {}", bindAddress);

								final SelectionKey acceptSelectionKey = InternalQueue.register(serverChannel);
								currentAcceptSelectionKey = acceptSelectionKey;
								
								acceptSelectionKey.attach(new SelectionKeyVisitor() {
									@Override
									public void visit(SelectionKey key) {
										if (!key.isAcceptable()) {
											return;
										}
										
										ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
										try {
											LOGGER.debug("-> Accepting client on: {}", bindAddress);
											final SocketChannel outboundChannel = ssc.accept();
											
											outboundChannels.add(outboundChannel);
											LOGGER.debug("-> Clients connected: {}", outboundChannels.size());
											
											final Connector connector = new InnerSocketReady(/*%% queue, */thisExecutor, outboundChannel).create();
											
											thisExecutor.execute(new Runnable() {
												@Override
												public void run() {
													listening.connecting(new Connector() {
														@Override
														public Connector send(Address address, ByteBuffer buffer) {
															connector.send(address, buffer);
															return connector;
														}
														@Override
														public Connector receiving(Receiver receiver) {
															connector.receiving(receiver);
															return connector;
														}
														@Override
														public Connector failing(final Failing failing) {
															connector.failing(new Failing() {
																@Override
																public void failed(final IOException e) {
																	removed(outboundChannel);
																	failing.failed(e);
																}
															});
															return connector;
														}
														@Override
														public Connector connecting(Connecting connecting) {
															connector.connecting(connecting);
															return connector;
														}
														@Override
														public Connector closing(final Closing closing) {
															connector.closing(new Closing() {
																@Override
																public void closed() {
																	removed(outboundChannel);
																	closing.closed();
																}
															});
															return connector;
														}
														
														@Override
														public Connector disconnect() {
															removed(outboundChannel);
															connector.disconnect();
															return connector;
														}
														@Override
														public Connector connect() {
															connector.connect();
															return connector;
														}
													});
												}
											});
										} catch (IOException e) {
											close(serverChannel, acceptSelectionKey);
											LOGGER.error("Error while accepting on: {}", bindAddress, e);
										}
									}
								});
	
								try {
									InetSocketAddress a;
									if (bindAddress.getHost() == null) {
										a = new InetSocketAddress(bindAddress.getPort()); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
									} else {
										a = new InetSocketAddress(bindAddress.getHost(), bindAddress.getPort()); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
									}
									if (a.isUnresolved()) {
										throw new IOException("Unresolved address: " + bindAddress.getHost() + ":" + bindAddress.getPort());
									}
									LOGGER.debug("-> Bound on: {}", a);
									serverChannel.socket().bind(a);
									acceptSelectionKey.interestOps(acceptSelectionKey.interestOps() | SelectionKey.OP_ACCEPT);
								} catch (IOException e) {
									close(serverChannel, acceptSelectionKey);
									throw new IOException("Could not bind to: " + bindAddress, e);
								}
	
							} catch (IOException e) {
								close(serverChannel, null);
								LOGGER.error("Error while creating server socket on: {}", bindAddress, e);
								if (thisFailing != null) {
									thisFailing.failed(e);
								}
								return;
							}
						} catch (IOException ee) {
							LOGGER.error("Error while creating server socket on: {}", bindAddress, ee);
							if (thisFailing != null) {
								thisFailing.failed(ee);
							}
							return;
						}

						if (thisAccepting != null) {
							thisAccepting.connected();
						}
					}
				});
				return this;
			}
			
			private void close(ServerSocketChannel serverChannel, SelectionKey acceptSelectionKey) {
				try {
					serverChannel.close();
				} catch (IOException e) {
				}
				if (acceptSelectionKey != null) {
					acceptSelectionKey.cancel();
				}
			}

			@Override
			public Acceptable close() {
				InternalQueue.execute(new Runnable() {
					@Override
					public void run() {
						for (SocketChannel s : outboundChannels) {
							try {
								s.close();
							} catch (IOException e) {
							}
						}
						outboundChannels.clear();

						if (currentChannel != null) {
							close(currentChannel, currentAcceptSelectionKey);
						}
						currentChannel = null;
						currentAcceptSelectionKey = null;
					}
				});
				return this;
			}
		};
		return acceptable;
	}
	
	private static final class InnerSocketReady {
		
		private static final Logger LOGGER = LoggerFactory.getLogger(SocketConnectorFactory.class);

		//%% private final InternalQueue queue;

		private final Executor thisExecutor;
		private final SocketChannel socketChannel;
		private Connector connectable = null;

		public InnerSocketReady(/*%% InternalQueue queue, */Executor executor, SocketChannel socketChannel) {
			//%% this.queue = queue;
			thisExecutor = executor;
			this.socketChannel = socketChannel;
		}
		
		public Connector create() {
			Connector c = connectable;
			if (c != null) {
				c.disconnect();
			}

			connectable = new Connector() {
				private Connecting connecting = null;
				private Closing closing = null;
				private Failing failing = null;
				private Receiver receiver = null;
				
				private SocketChannel currentChannel = null;
				private SelectionKey currentSelectionKey = null;

				private final Deque<ByteBuffer> toWriteQueue = new LinkedList<>();
				private long toWriteLength = 0L;

				@Override
				public Connector closing(Closing closing) {
					this.closing = closing;
					return this;
				}
			
				@Override
				public Connector connecting(Connecting connecting) {
					this.connecting = connecting;
					return null;
				}
				
				@Override
				public Connector failing(Failing failing) {
					this.failing = failing;
					return null;
				}
				
				@Override
				public Connector receiving(Receiver receiver) {
					this.receiver = receiver;
					return null;
				}
				
				@Override
				public Connector connect() {
					final Connecting thisConnecting = connecting;
					final Closing thisClosing = closing;
					final Failing thisFailing = failing;
					final Receiver thisReceiver = receiver;

					InternalQueue.execute(new Runnable() {
						@Override
						public void run() {
							if (currentChannel != null) {
								throw new IllegalStateException();
							}
							if (currentSelectionKey != null) {
								throw new IllegalStateException();
							}
							
							try {
								final SocketChannel channel = socketChannel;
								currentChannel = channel;
								try {
									// channel.socket().setSoTimeout((int) (TIMEOUT * 1000d)); // Not working with NIO
									channel.configureBlocking(false);

									final SelectionKey selectionKey = InternalQueue.register(channel);
									currentSelectionKey = selectionKey;
		
									selectionKey.attach(new SelectionKeyVisitor() {
										@Override
										public void visit(SelectionKey key) {
											if (!channel.isOpen()) {
												return;
											}
											if (key.isReadable()) {
												final ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
												try {
													if (channel.read(readBuffer) < 0) {
														disconnect(channel, selectionKey);
														currentChannel = null;
														currentSelectionKey = null;
														if (thisClosing != null) {
															thisExecutor.execute(new Runnable() {
																@Override
																public void run() {
																	thisClosing.closed();
																}
															});
														}
														return;
													}
												} catch (IOException e) {
													LOGGER.trace("Connection failed", e);
													disconnect(channel, selectionKey);
													currentChannel = null;
													currentSelectionKey = null;
													if (thisClosing != null) {
														thisExecutor.execute(new Runnable() {
															@Override
															public void run() {
																thisClosing.closed();
															}
														});
													}
													return;
												}
												
												readBuffer.flip();
												if (thisReceiver != null) {
													thisExecutor.execute(new Runnable() {
														@Override
														public void run() {
															thisReceiver.received(null, readBuffer);
														}
													});
												}
											} else if (key.isWritable()) {
												while (true) {
													ByteBuffer b = toWriteQueue.peek();
													if (b == null) {
														break;
													}
													long before = b.remaining();
													try {
														channel.write(b);
														toWriteLength -= before - b.remaining();
													} catch (IOException e) {
														LOGGER.trace("Connection failed", e);
														disconnect(channel, selectionKey);
														currentChannel = null;
														currentSelectionKey = null;
														if (thisClosing != null) {
															thisExecutor.execute(new Runnable() {
																@Override
																public void run() {
																	thisClosing.closed();
																}
															});
														}
														return;
													}
													
													if (b.hasRemaining()) {
														return;
													}
													
													toWriteQueue.remove();
												}
												if (!channel.isOpen()) {
													return;
												}
												if (!selectionKey.isValid()) {
													return;
												}
												selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
											}
										}
									});
				
									selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_READ);
									if (!toWriteQueue.isEmpty()) {
										selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
									}
		
								} catch (IOException e) {
									LOGGER.trace("Connection failed", e);
									disconnect(channel, null);
									currentChannel = null;
									currentSelectionKey = null;
									throw e;
								}
					
							} catch (final IOException e) {
								if (thisFailing != null) {
									thisExecutor.execute(new Runnable() {
										@Override
										public void run() {
											thisFailing.failed(e);
										}
									});
								}
								return;
							}

							if (thisConnecting != null) {
								thisExecutor.execute(new Runnable() {
									@Override
									public void run() {
										thisConnecting.connected();
									}
								});
							}
						}
					});
					
					return this;
				}
				
				private void disconnect(SocketChannel channel, SelectionKey selectionKey) {
					try {
						channel.socket().close();
					} catch (IOException e) {
					}
					try {
						channel.close();
					} catch (IOException e) {
					}
					if (selectionKey != null) {
						selectionKey.cancel();
					}
				}

				@Override
				public Connector disconnect() {
					InternalQueue.execute(new Runnable() {
						@Override
						public void run() {
							if (currentChannel != null) {
								disconnect(currentChannel, currentSelectionKey);
							}
							currentChannel = null;
							currentSelectionKey = null;
						}
					});
					
					return this;
				}
				
				@Override
				public Connector send(final Address address, final ByteBuffer buffer) {
					InternalQueue.execute(new Runnable() {
						@Override
						public void run() {
							if (address != null) {
								LOGGER.warn("Ignored send address: {}", address);
							}
							
							if ((WRITE_MAX_BUFFER_SIZE > 0L) && (toWriteLength > WRITE_MAX_BUFFER_SIZE)) {
								LOGGER.warn("Dropping {} bytes that should have been sent to {}", buffer.remaining(), address);
								return;
							}
							
							toWriteQueue.add(buffer);
							toWriteLength += buffer.remaining();
							LOGGER.trace("Write buffer: {} bytes (current size: {} bytes)", buffer.remaining(), toWriteLength);
							
							SocketChannel channel = currentChannel;
							SelectionKey selectionKey = currentSelectionKey;
							if (channel == null) {
								return;
							}
							if (selectionKey == null) {
								return;
							}
							if (!channel.isOpen()) {
								return;
							}
							if (!selectionKey.isValid()) {
								return;
							}
							selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
						}
					});
					
					return this;
				}
			};
			
			return connectable;
		}
	}

}
