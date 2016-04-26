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

public final class TcpSocketServer implements Disconnectable {
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpSocketServer.class);

	private static final Config CONFIG = ConfigFactory.load(TcpSocketServer.class.getClassLoader());
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.read.size").intValue();
	// private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.write.size").intValue();
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.write.buffer").longValue();
	// private static final double TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.socket.timeout");

	public static interface Builder extends NinioBuilder<Disconnectable> {
		Builder with(Executor executor);
		Builder bind(Address bindAddress);

		Builder failing(Failing failing);
		Builder connecting(ListenConnecting connecting);
		Builder listening(Listening listening);
	}

	public static Builder builder() {
		return new Builder() {
			private Executor executor = Shared.EXECUTOR;
			
			private ListenConnecting connecting = null;
			private Listening listening = null;
			private Failing failing = null;
			
			private Address bindAddress = null;
			
			@Override
			public Builder connecting(ListenConnecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public Builder listening(Listening listening) {
				this.listening = listening;
				return this;
			}
			
			@Override
			public Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public Builder bind(Address bindAddress) {
				this.bindAddress = bindAddress;
				return this;
			}

			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}

			@Override
			public Disconnectable create(Queue queue) {
				return new TcpSocketServer(queue, executor, bindAddress, connecting, listening, failing);
			}
		};
	}
	
	private final Set<SocketChannel> outboundChannels = new HashSet<>();
	
	private final Queue queue;

	private ServerSocketChannel currentChannel = null;
	private SelectionKey currentAcceptSelectionKey = null;

	private void removed(final SocketChannel outboundChannel) {
		queue.execute(new Runnable() {
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
	
	private TcpSocketServer(final Queue queue, final Executor executor, final Address bindAddress, final ListenConnecting connecting, final Listening listening, final Failing failing) {
		this.queue = queue;
		
		queue.execute(new Runnable() {
			@Override
			public void run() {
				try {
					final ServerSocketChannel serverChannel = ServerSocketChannel.open();
					currentChannel = serverChannel;
					try {
						serverChannel.configureBlocking(false);
						// serverChannel.socket().setReceiveBufferSize();
						
						LOGGER.debug("-> Server channel ready to accept on: {}", bindAddress);

						final SelectionKey acceptSelectionKey = queue.register(serverChannel);
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
									
									final Runnable remove = new Runnable() {
										@Override
										public void run() {
											removed(outboundChannel);
										}
									};

									executor.execute(new Runnable() {
										@Override
										public void run() {
											InnerListenConnectingableConnectorBuilder builder = new InnerListenConnectingableConnectorBuilder();
											listening.connecting(builder);
											new InnerSocketReady(queue, executor, outboundChannel, builder.connecting, builder.closing, builder.failing, builder.receiver, remove);
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
						if (failing != null) {
							failing.failed(e);
						}
						return;
					}
				} catch (IOException ee) {
					LOGGER.error("Error while creating server socket on: {}", bindAddress, ee);
					if (failing != null) {
						failing.failed(ee);
					}
					return;
				}

				if (connecting != null) {
					connecting.connected(TcpSocketServer.this);
				}
			}
		});
	}
	
	private static final class InnerListenConnectingableConnectorBuilder implements Listening.ConnectorBuilder {
		public Connecting connecting = null;
		public Closing closing = null;
		public Failing failing = null;
		public Receiver receiver = null;
		
		public InnerListenConnectingableConnectorBuilder() {
		}
		
		@Override
		public Listening.ConnectorBuilder closing(Closing closing) {
			this.closing = closing;
			return this;
		}
	
		@Override
		public Listening.ConnectorBuilder connecting(Connecting connecting) {
			this.connecting = connecting;
			return this;
		}
		
		@Override
		public Listening.ConnectorBuilder failing(Failing failing) {
			this.failing = failing;
			return this;
		}
		
		@Override
		public Listening.ConnectorBuilder receiving(Receiver receiver) {
			this.receiver = receiver;
			return this;
		}
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
		queue.execute(new Runnable() {
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
	
	private static final class InnerSocketReady implements Connector {
		
		private static final Logger LOGGER = LoggerFactory.getLogger(TcpSocket.class);

		private final Queue queue;

		private SocketChannel currentChannel = null;
		private SelectionKey currentSelectionKey = null;

		private final Deque<ByteBuffer> toWriteQueue = new LinkedList<>();
		private long toWriteLength = 0L;
		
		private final Runnable remove;
		
		public InnerSocketReady(final Queue queue, final Executor executor, final SocketChannel socketChannel, final Connecting connecting, final Closing closing, final Failing failing, final Receiver receiver, final Runnable remove) {
			this.queue = queue;
			this.remove = remove;

			queue.execute(new Runnable() {
				@Override
				public void run() {
					try {
						final SocketChannel channel = socketChannel;
						currentChannel = channel;
						try {
							// channel.socket().setSoTimeout((int) (TIMEOUT * 1000d)); // Not working with NIO
							channel.configureBlocking(false);

							final SelectionKey selectionKey = queue.register(channel);
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
												if (closing != null) {
													executor.execute(new Runnable() {
														@Override
														public void run() {
															closing.closed();
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
											if (closing != null) {
												executor.execute(new Runnable() {
													@Override
													public void run() {
														closing.closed();
													}
												});
											}
											return;
										}
										
										readBuffer.flip();
										if (receiver != null) {
											executor.execute(new Runnable() {
												@Override
												public void run() {
													receiver.received(InnerSocketReady.this, null, readBuffer);
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
												if (closing != null) {
													executor.execute(new Runnable() {
														@Override
														public void run() {
															closing.closed();
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
						remove.run();
						if (failing != null) {
							executor.execute(new Runnable() {
								@Override
								public void run() {
									failing.failed(e);
								}
							});
						}
						return;
					}

					if (connecting != null) {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								connecting.connected(InnerSocketReady.this);
							}
						});
					}
				}
			});
		}
				
		private void disconnect(SocketChannel channel, SelectionKey selectionKey) {
			remove.run();

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
		public void close() {
			queue.execute(new Runnable() {
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
		public Connector send(final Address address, final ByteBuffer buffer) {
			queue.execute(new Runnable() {
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
	}
}
