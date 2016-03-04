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
	
	private Executor executor = null;
	private Address bindAddress = null;
	private Acceptable acceptable = null;

	public SocketServer() {
	}
	
	public SocketServer with(Executor executor) {
		this.executor = executor;
		return this;
	}

	public SocketServer bind(Address bindAddress) {
		this.bindAddress = bindAddress;
		return this;
	}

	private void removed(final SocketChannel outboundChannel) {
		InternalQueue.EXECUTOR.execute(new Runnable() {
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
	
	public Acceptable create() {
		Acceptable c = acceptable;
		if (c != null) {
			c.close();
		}
		final Executor thisExecutor = executor;
		final Address thisBindAddress = bindAddress;
		acceptable = new Acceptable() {
			private ServerSocketChannel currentChannel = null;
			private SelectionKey currentAcceptSelectionKey = null;

			private Accepting accepting = null;
			private Failing failing = null;
			
			@Override
			public void accepting(Accepting accepting) {
				this.accepting = accepting;
			}
			@Override
			public void failing(Failing failing) {
				this.failing = failing;
			}
			
			@Override
			public void accept(final ListenConnectingable listening) {
				final Accepting thisAccepting = accepting;
				final Failing thisFailing = failing;
				
				InternalQueue.EXECUTOR.execute(new Runnable() {
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
								
								LOGGER.debug("-> Server channel ready to accept on: {}", thisBindAddress);

								final SelectionKey acceptSelectionKey = serverChannel.register(InternalQueue.SELECTOR, 0);
								currentAcceptSelectionKey = acceptSelectionKey;
								
								acceptSelectionKey.attach(new SelectionKeyVisitor() {
									@Override
									public void visit(SelectionKey key) {
										if (!key.isAcceptable()) {
											return;
										}
										
										ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
										try {
											LOGGER.debug("-> Accepting client on: {}", thisBindAddress);
											final SocketChannel outboundChannel = ssc.accept();
											
											outboundChannels.add(outboundChannel);
											LOGGER.debug("-> Clients connected: {}", outboundChannels.size());
											
											final Connector connectable = new InnerSocketReady(thisExecutor, outboundChannel).create();
											
											thisExecutor.execute(new Runnable() {
												@Override
												public void run() {
													listening.connecting(new Connector() {
														@Override
														public void send(Address address, ByteBuffer buffer) {
															connectable.send(address, buffer);
														}
														@Override
														public void receiving(Receiver receiver) {
															connectable.receiving(receiver);
														}
														@Override
														public void failing(final Failing failing) {
															connectable.failing(new Failing() {
																@Override
																public void failed(final IOException e) {
																	removed(outboundChannel);
																	failing.failed(e);
																}
															});
														}
														@Override
														public void connecting(Connecting connecting) {
															connectable.connecting(connecting);
														}
														@Override
														public void closing(final Closing closing) {
															connectable.closing(new Closing() {
																@Override
																public void closed() {
																	removed(outboundChannel);
																	closing.closed();
																}
															});
														}
														
														@Override
														public void disconnect() {
															removed(outboundChannel);
															connectable.disconnect();
														}
														@Override
														public void connect() {
															connectable.connect();
														}
													});
												}
											});
										} catch (IOException e) {
											close(serverChannel, acceptSelectionKey);
											LOGGER.error("Error while accepting on: {}", thisBindAddress, e);
										}
									}
								});
	
								try {
									InetSocketAddress a;
									if (thisBindAddress.getHost() == null) {
										a = new InetSocketAddress(thisBindAddress.getPort()); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
									} else {
										a = new InetSocketAddress(thisBindAddress.getHost(), thisBindAddress.getPort()); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
									}
									if (a.isUnresolved()) {
										throw new IOException("Unresolved address: " + thisBindAddress.getHost() + ":" + thisBindAddress.getPort());
									}
									LOGGER.debug("-> Bound on: {}", a);
									serverChannel.socket().bind(a);
									acceptSelectionKey.interestOps(acceptSelectionKey.interestOps() | SelectionKey.OP_ACCEPT);
								} catch (IOException e) {
									close(serverChannel, acceptSelectionKey);
									throw new IOException("Could not bind to: " + thisBindAddress, e);
								}
	
							} catch (IOException e) {
								close(serverChannel, null);
								LOGGER.error("Error while creating server socket on: {}", thisBindAddress, e);
								if (thisFailing != null) {
									thisFailing.failed(e);
								}
								return;
							}
						} catch (IOException ee) {
							LOGGER.error("Error while creating server socket on: {}", thisBindAddress, ee);
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
			public void close() {
				InternalQueue.EXECUTOR.execute(new Runnable() {
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
			}
		};
		return acceptable;
	}
	
	private static final class InnerSocketReady {
		
		private static final Logger LOGGER = LoggerFactory.getLogger(SocketConnectorFactory.class);

		private final Executor executor;
		private final SocketChannel socketChannel;
		private Connector connectable = null;

		public InnerSocketReady(Executor executor, SocketChannel socketChannel) {
			this.socketChannel = socketChannel;
			this.executor = executor;
		}

		public Connector create() {
			Connector c = connectable;
			if (c != null) {
				c.disconnect();
			}
			connectable = new SimpleConnector(executor, new SimpleConnector.Connect() {
				private SocketChannel currentChannel = null;
				private SelectionKey currentSelectionKey = null;

				private final Deque<ByteBuffer> toWriteQueue = new LinkedList<>();
				private long toWriteLength = 0L;

				@Override
				public void connect(final Connecting connecting, final Closing closing, final Failing failing, final Receiver receiver) {
					InternalQueue.EXECUTOR.execute(new Runnable() {
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

									final SelectionKey selectionKey = channel.register(InternalQueue.SELECTOR, 0);
									currentSelectionKey = selectionKey;
		
									selectionKey.attach(new SelectionKeyVisitor() {
										@Override
										public void visit(SelectionKey key) {
											if (!channel.isOpen()) {
												return;
											}
											if (key.isReadable()) {
												ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
												try {
													if (channel.read(readBuffer) < 0) {
														disconnect(channel, selectionKey);
														currentChannel = null;
														currentSelectionKey = null;
														if (closing != null) {
															closing.closed();
														}
														readBuffer = null;
													}
												} catch (IOException e) {
													LOGGER.trace("Connection failed", e);
													disconnect(channel, selectionKey);
													currentChannel = null;
													currentSelectionKey = null;
													if (closing != null) {
														closing.closed();
													}
													readBuffer = null;
												}
												if (readBuffer != null) {
													readBuffer.flip();
													if (receiver != null) {
														receiver.received(null, readBuffer);
													}
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
														if (closing != null) {
															closing.closed();
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
					
							} catch (IOException e) {
								if (failing != null) {
									failing.failed(e);
								}
								return;
							}

							if (connecting != null) {
								connecting.connected();
							}
						}
					});
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
				public void disconnect() {
					InternalQueue.EXECUTOR.execute(new Runnable() {
						@Override
						public void run() {
							if (currentChannel != null) {
								disconnect(currentChannel, currentSelectionKey);
							}
							currentChannel = null;
							currentSelectionKey = null;
						}
					});
				}
				
				@Override
				public void send(final Address address, final ByteBuffer buffer) {
					InternalQueue.EXECUTOR.execute(new Runnable() {
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
				}
			});
			return connectable;
		}
	}

}
