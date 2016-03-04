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

public final class SocketReady {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SocketReady.class);

	private static final Config CONFIG = ConfigFactory.load(SocketReady.class.getClassLoader());
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.read.size").intValue();
	// private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.write.size").intValue();
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.write.buffer").longValue();
	// private static final double TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.socket.timeout");

	private Executor executor = null;
	private Address connectAddress = null;
	private Connectable connectable = null;

	public SocketReady() {
	}
	
	public SocketReady with(Executor executor) {
		this.executor = executor;
		return this;
	}

	public SocketReady connect(Address connectAddress) {
		this.connectAddress = connectAddress;
		return this;
	}

	public Connectable create() {
		Connectable c = connectable;
		if (c != null) {
			c.disconnect();
		}
		final Address thisConnectAddress = connectAddress;
		connectable = new SimpleConnectable(executor, new SimpleConnectable.Connect() {
			private SocketChannel currentChannel = null;
			private SelectionKey currentInboundKey = null;
			private SelectionKey currentSelectionKey = null;

			private final Deque<ByteBuffer> toWriteQueue = new LinkedList<>();
			private long toWriteLength = 0L;

			@Override
			public void connect(final Connecting connecting, final Closing closing, final Failing failing, final Receiver receiver) {
				InternalQueue.post(new Runnable() {
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
								final SelectionKey inboundKey = channel.register(InternalQueue.selector, SelectionKey.OP_CONNECT);
								currentInboundKey = inboundKey;
								inboundKey.attach(new SelectionKeyVisitor() {
									@Override
									public void visit(SelectionKey key) {
										if (!key.isConnectable()) {
											return;
										}
						
										try {
											channel.finishConnect();
											final SelectionKey selectionKey = channel.register(InternalQueue.selector, 0);
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
																disconnect(channel, inboundKey, selectionKey);
																currentChannel = null;
																currentInboundKey = null;
																currentSelectionKey = null;
																if (closing != null) {
																	closing.closed();
																}
																readBuffer = null;
															}
														} catch (IOException e) {
															LOGGER.trace("Connection failed", e);
															disconnect(channel, inboundKey, selectionKey);
															currentChannel = null;
															currentInboundKey = null;
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
																disconnect(channel, inboundKey, selectionKey);
																currentChannel = null;
																currentInboundKey = null;
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
											disconnect(channel, inboundKey, null);
											currentChannel = null;
											currentInboundKey = null;
											currentSelectionKey = null;
											if (failing != null) {
												failing.failed(e);
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
			public void disconnect() {
				InternalQueue.post(new Runnable() {
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
			}
			
			@Override
			public void send(final Address address, final ByteBuffer buffer) {
				InternalQueue.post(new Runnable() {
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
