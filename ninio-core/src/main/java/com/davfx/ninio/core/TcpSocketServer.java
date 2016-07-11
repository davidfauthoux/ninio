package com.davfx.ninio.core;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

public final class TcpSocketServer implements Disconnectable {
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpSocketServer.class);

	private static final Config CONFIG = ConfigUtils.load(TcpSocketServer.class);
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("socket.write.buffer").longValue();
	// private static final double TIMEOUT = ConfigUtils.getDuration(CONFIG, "socket.timeout");

	public static interface Builder extends NinioBuilder<Disconnectable> {
		Builder with(ByteBufferAllocator byteBufferAllocator);
		Builder bind(Address bindAddress);

		Builder closing(Closing closing);
		Builder failing(Failing failing);
		Builder connecting(ListenConnecting connecting);
		Builder listening(Listening listening);
	}

	public static Builder builder() {
		return new Builder() {
			private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();
			
			private ListenConnecting connecting = null;
			private Listening listening = null;
			private Failing failing = null;
			private Closing closing = null;
			
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
			public Builder closing(Closing closing) {
				this.closing = closing;
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

			@Override
			public Builder with(ByteBufferAllocator byteBufferAllocator) {
				this.byteBufferAllocator = byteBufferAllocator;
				return this;
			}

			@Override
			public Disconnectable create(Queue queue) {
				if (bindAddress == null) {
					throw new NullPointerException("bindAddress");
				}
				return new TcpSocketServer(queue, byteBufferAllocator, bindAddress, connecting, listening, failing, closing);
			}
		};
	}
	
	private final Set<SocketChannel> outboundChannels = new HashSet<>();
	
	private final Queue queue;
	private final Closing closing;
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
	
	private TcpSocketServer(final Queue queue, final ByteBufferAllocator byteBufferAllocator, final Address bindAddress, final ListenConnecting connecting, final Listening listening, final Failing failing, Closing closing) {
		this.queue = queue;
		this.closing = closing;
		
		queue.execute(new Runnable() {
			@Override
			public void run() {
				try {
					final ServerSocketChannel serverChannel = ServerSocketChannel.open();
					currentChannel = serverChannel;
					try {
						serverChannel.configureBlocking(false);
						//%% serverChannel.socket().setReceiveBufferSize();
						
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
									final Address clientAddress = new Address(outboundChannel.socket().getInetAddress().getHostAddress(), outboundChannel.socket().getPort());

									outboundChannels.add(outboundChannel);
									LOGGER.debug("-> Clients connected: {}", outboundChannels.size());
									
									final Runnable remove = new Runnable() {
										@Override
										public void run() {
											removed(outboundChannel);
										}
									};

									final InnerSocketContext context = new InnerSocketContext();
									
									final Connector innerConnector = new Connector() {
										private void disconnect(SocketChannel channel, SelectionKey selectionKey, Closing closing) {
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

											if (closing != null) {
												closing.closed();
											}
										}

										@Override
										public void close() {
											queue.execute(new Runnable() {
												@Override
												public void run() {
													if (context.currentChannel != null) {
														disconnect(context.currentChannel, context.currentSelectionKey, context.closing);
													}
													context.currentChannel = null;
													context.currentSelectionKey = null;
												}
											});
											//%% queue.waitFor();
										}
										
										@Override
										public Connector send(final Address address, final ByteBuffer buffer) {
											queue.execute(new Runnable() {
												@Override
												public void run() {
													if (address != null) {
														LOGGER.warn("Ignored send address: {}", address);
													}
													
													if ((WRITE_MAX_BUFFER_SIZE > 0L) && (context.toWriteLength > WRITE_MAX_BUFFER_SIZE)) {
														LOGGER.warn("Dropping {} bytes that should have been sent to {}", buffer.remaining(), address);
														return;
													}

													context.toWriteQueue.add(buffer);
													context.toWriteLength += buffer.remaining();
													LOGGER.trace("Write buffer: {} bytes (current size: {} bytes)", buffer.remaining(), context.toWriteLength);
													
													SocketChannel channel = context.currentChannel;
													SelectionKey selectionKey = context.currentSelectionKey;
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
									
									Listening.Connection connection = listening.connecting(clientAddress, innerConnector);

									final Connecting connecting = connection.connecting();
									final Closing closing = connection.closing();
									final Failing failing = connection.failing();
									final Receiver receiver = connection.receiver();
									final Buffering buffering = connection.buffering();

									queue.execute(new Runnable() {
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

											if (closing != null) {
												closing.closed();
											}
										}

										@Override
										public void run() {
											context.closing = closing;
											try {
												final SocketChannel channel = outboundChannel;
												context.currentChannel = channel;
												try {
													// channel.socket().setSoTimeout((int) (TIMEOUT * 1000d)); // Not working with NIO
													channel.configureBlocking(false);

													final SelectionKey selectionKey = queue.register(channel);
													context.currentSelectionKey = selectionKey;

													selectionKey.attach(new SelectionKeyVisitor() {
														@Override
														public void visit(SelectionKey key) {
															if (!channel.isOpen()) {
																return;
															}
															if (key.isReadable()) {
																final ByteBuffer readBuffer = byteBufferAllocator.allocate();
																try {
																	if (channel.read(readBuffer) < 0) {
																		disconnect(channel, selectionKey);
																		context.currentChannel = null;
																		context.currentSelectionKey = null;
																		return;
																	}
																} catch (IOException e) {
																	LOGGER.trace("Connection failed", e);
																	disconnect(channel, selectionKey);
																	context.currentChannel = null;
																	context.currentSelectionKey = null;
																	return;
																}
																
																readBuffer.flip();
																if (receiver != null) {
																	receiver.received(innerConnector, null, readBuffer);
																}
															} else if (key.isWritable()) {
																while (true) {
																	ByteBuffer b = context.toWriteQueue.peek();
																	if (b == null) {
																		break;
																	}
																	long before = b.remaining();
																	try {
																		channel.write(b);
																		context.toWriteLength -= before - b.remaining();
																	} catch (IOException e) {
																		LOGGER.trace("Connection failed", e);
																		disconnect(channel, selectionKey);
																		currentChannel = null;
																		context.currentSelectionKey = null;
																		return;
																	}
																	
																	if (buffering != null) {
																		buffering.buffering(context.toWriteLength);
																	}
																	
																	if (b.hasRemaining()) {
																		return;
																	}
																	
																	context.toWriteQueue.remove();
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
													if (!context.toWriteQueue.isEmpty()) {
														selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
													}

												} catch (IOException e) {
													LOGGER.trace("Connection failed", e);
													disconnect(channel, null);
													currentChannel = null;
													context.currentSelectionKey = null;
													throw e;
												}
									
											} catch (final IOException e) {
												remove.run();
												if (failing != null) {
													failing.failed(e);
												}
												return;
											}

											if (connecting != null) {
												connecting.connected(innerConnector, clientAddress);
											}
										}
									});
								} catch (IOException e) {
									close(serverChannel, acceptSelectionKey);
									LOGGER.error("Error while accepting on: {}", bindAddress, e);
								}
							}
						});

						try {
							InetSocketAddress a = new InetSocketAddress(bindAddress.host, bindAddress.port); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
							if (a.isUnresolved()) {
								throw new IOException("Unresolved address: " + bindAddress);
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
	
	private void close(ServerSocketChannel serverChannel, SelectionKey acceptSelectionKey) {
		try {
			serverChannel.close();
		} catch (IOException e) {
		}
		if (acceptSelectionKey != null) {
			acceptSelectionKey.cancel();
		}
		
		if (closing != null) {
			closing.closed();
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
		//%% queue.waitFor();
	}
	
	private static final class InnerSocketContext {
		public Closing closing = null;
		public SocketChannel currentChannel = null;
		public SelectionKey currentSelectionKey = null;

		public final Deque<ByteBuffer> toWriteQueue = new LinkedList<>();
		public long toWriteLength = 0L;
		
		public InnerSocketContext() {
		}
	}
}
