package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class SocketConnectorFactory implements ConnectorFactory {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SocketConnectorFactory.class);

	private static final Config CONFIG = ConfigFactory.load(SocketConnectorFactory.class.getClassLoader());
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.read.size").intValue();
	// private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.write.size").intValue();
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.write.buffer").longValue();
	// private static final double TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.socket.timeout");

	//%% private final InternalQueue queue;

	private Executor executor = Shared.EXECUTOR;
	
	private Address connectAddress = null;
	private Connector connectable = null;

	public SocketConnectorFactory() {
	}

	/*%%
	public SocketConnectorFactory(InternalQueue queue) {
		this.queue = queue;
	}
	*/
	
	public SocketConnectorFactory with(Executor executor) {
		this.executor = executor;
		return this;
	}

	public SocketConnectorFactory connect(Address connectAddress) {
		this.connectAddress = connectAddress;
		return this;
	}

	@Override
	public Connector create() {
		Connector c = connectable;
		connectable = null;
		if (c != null) {
			c.disconnect();
		}
		
		final Executor thisExecutor = executor;
		final Address thisConnectAddress = connectAddress;
		
		connectable = new Connector() {
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
			private SocketChannel currentChannel = null;
			private SelectionKey currentInboundKey = null;
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
						if (currentInboundKey != null) {
							throw new IllegalStateException();
						}
						if (currentSelectionKey != null) {
							throw new IllegalStateException();
						}
						
						try {
							final SocketChannel channel = SocketChannel.open();
							currentChannel = channel;
							try {
								// channel.socket().setSoTimeout((int) (TIMEOUT * 1000d)); // Not working with NIO
								channel.configureBlocking(false);
								final SelectionKey inboundKey = InternalQueue.register(channel);
								inboundKey.interestOps(inboundKey.interestOps() | SelectionKey.OP_CONNECT);
								currentInboundKey = inboundKey;
								inboundKey.attach(new SelectionKeyVisitor() {
									@Override
									public void visit(SelectionKey key) {
										if (!key.isConnectable()) {
											return;
										}
						
										try {
											channel.finishConnect();
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
																disconnect(channel, inboundKey, selectionKey);
																currentChannel = null;
																currentInboundKey = null;
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
															disconnect(channel, inboundKey, selectionKey);
															currentChannel = null;
															currentInboundKey = null;
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
																LOGGER.trace("Actual write buffer: {} bytes", b.remaining());
																channel.write(b);
																toWriteLength -= before - b.remaining();
															} catch (IOException e) {
																LOGGER.trace("Connection failed", e);
																disconnect(channel, inboundKey, selectionKey);
																currentChannel = null;
																currentInboundKey = null;
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
				
											if (thisConnecting != null) {
												thisExecutor.execute(new Runnable() {
													@Override
													public void run() {
														thisConnecting.connected();
													}
												});
											}
											
										} catch (final IOException e) {
											LOGGER.trace("Connection failed", e);
											disconnect(channel, inboundKey, null);
											currentChannel = null;
											currentInboundKey = null;
											currentSelectionKey = null;
											if (thisFailing != null) {
												thisExecutor.execute(new Runnable() {
													@Override
													public void run() {
														thisFailing.failed(e);
													}
												});
											}
										}
									}
								});
								
								if (thisConnectAddress != null) {
									try {
										InetSocketAddress a = new InetSocketAddress(thisConnectAddress.getHost(), thisConnectAddress.getPort()); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
										if (a.isUnresolved()) {
											throw new IOException("Unresolved address: " + thisConnectAddress.getHost() + ":" + thisConnectAddress.getPort());
										}
										LOGGER.debug("Connecting to: {}", a);
										channel.connect(a);
									} catch (IOException e) {
										disconnect(channel, inboundKey, null);
										throw new IOException("Could not connect to: " + thisConnectAddress, e);
									}
								}
							} catch (IOException e) {
								disconnect(channel, null, null);
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
					}
				});
				
				return this;
			}
			
			private void disconnect(SocketChannel channel, SelectionKey inboundKey, SelectionKey selectionKey) {
				try {
					channel.socket().close();
				} catch (IOException e) {
				}
				try {
					channel.close();
				} catch (IOException e) {
				}
				if (inboundKey != null) {
					inboundKey.cancel();
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
							disconnect(currentChannel, currentInboundKey, currentSelectionKey);
						}
						currentChannel = null;
						currentInboundKey = null;
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
