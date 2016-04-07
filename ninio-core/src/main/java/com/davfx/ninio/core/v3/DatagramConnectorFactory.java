package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class DatagramConnectorFactory implements ConnectorFactory {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DatagramConnectorFactory.class);

	private static final Config CONFIG = ConfigFactory.load(DatagramConnectorFactory.class.getClassLoader());
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.read.size").intValue();
	private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.write.size").intValue();
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.write.buffer").longValue();

	private static final class AddressedByteBuffer {
		Address address;
		ByteBuffer buffer;
	}

	//%% private final InternalQueue queue;

	private Executor executor = Shared.EXECUTOR;
	
	private Address bindAddress = null;
	private Connector connectable = null;

	public DatagramConnectorFactory() {
	}
	/*%%
	public DatagramConnectorFactory(InternalQueue queue) {
		this.queue = queue;
	}
	*/
	
	public DatagramConnectorFactory with(Executor executor) {
		this.executor = executor;
		return this;
	}

	public DatagramConnectorFactory bind(Address bindAddress) {
		this.bindAddress = bindAddress;
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
		final Address thisBindAddress = bindAddress;
		
		connectable = new Connector() {
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
			private DatagramChannel currentChannel = null;
			private SelectionKey currentSelectionKey = null;

			private final Deque<AddressedByteBuffer> toWriteQueue = new LinkedList<>();
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
							final DatagramChannel channel = DatagramChannel.open();
							currentChannel = channel;
							try {
								channel.configureBlocking(false);
								channel.socket().setReceiveBufferSize(READ_BUFFER_SIZE);
								channel.socket().setSendBufferSize(WRITE_BUFFER_SIZE);
								final SelectionKey selectionKey = InternalQueue.register(channel);
								currentSelectionKey = selectionKey;
								
								selectionKey.attach(new SelectionKeyVisitor() {
									@Override
									public void visit(final SelectionKey key) {
										if (!channel.isOpen()) {
											return;
										}
										
										if (key.isReadable()) {
											final ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
											InetSocketAddress from;
											try {
												from = (InetSocketAddress) channel.receive(readBuffer);
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
												final Address a = new Address(from.getHostString(), from.getPort());
												thisExecutor.execute(new Runnable() {
													@Override
													public void run() {
														thisReceiver.received(a, readBuffer);
													}
												});
											}
										} else if (key.isWritable()) {
											while (true) {
												AddressedByteBuffer b = toWriteQueue.peek();
												if (b == null) {
													break;
												}
												if (b.address == null) {
													try {
														channel.write(b.buffer);
													} catch (IOException e) {
														LOGGER.trace("Connection failed", e);
														disconnect(channel, selectionKey);
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
												} else {
													InetSocketAddress a;
													try {
														a = new InetSocketAddress(b.address.getHost(), b.address.getPort()); // Note this call blocks to resolve host (DNS resolution) //TODO Test unresolved
														if (a.isUnresolved()) {
															throw new IOException("Unresolved address: " + b.address.getHost() + ":" + b.address.getPort());
														}
													} catch (IOException e) {
														LOGGER.warn("Invalid address: {}", b.address);
														b.buffer.position(b.buffer.position() + b.buffer.remaining());
														a = null;
													}
													
													if (a != null) {
														long before = b.buffer.remaining();
														try {
															channel.send(b.buffer, a);
															toWriteLength -= before - b.buffer.remaining();
														} catch (IOException e) {
															LOGGER.warn("Write failed to: {}", a, e);
															b.buffer.position(b.buffer.position() + b.buffer.remaining());
														}
													}
												}
												
												if (b.buffer.hasRemaining()) {
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
								
								if (thisBindAddress != null) {
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
										channel.socket().bind(a);
									} catch (IOException e) {
										disconnect(channel, selectionKey);
										throw new IOException("Could not bind to: " + thisBindAddress, e);
									}
								}
							} catch (IOException e) {
								disconnect(channel, null);
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
			
			private void disconnect(DatagramChannel channel, SelectionKey selectionKey) {
				channel.socket().close();
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
						if ((WRITE_MAX_BUFFER_SIZE > 0L) && (toWriteLength > WRITE_MAX_BUFFER_SIZE)) {
							LOGGER.warn("Dropping {} bytes that should have been sent to {}", buffer.remaining(), address);
							return;
						}
						
						AddressedByteBuffer b = new AddressedByteBuffer();
						b.address = address;
						b.buffer = buffer;
						toWriteQueue.add(b);
						toWriteLength += b.buffer.remaining();
						LOGGER.trace("Write buffer: {} bytes (to {}) (current size: {} bytes)", b.buffer.remaining(), address, toWriteLength);
						
						DatagramChannel channel = currentChannel;
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
