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

public final class DatagramReady {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DatagramReady.class);

	private static final Config CONFIG = ConfigFactory.load(DatagramReady.class.getClassLoader());
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.read.size").intValue();
	private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.write.size").intValue();
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.write.buffer").longValue();

	private static final class AddressedByteBuffer {
		Address address;
		ByteBuffer buffer;
	}

	private Executor executor = null;
	private Address bindAddress = null;
	private Connectable connectable = null;

	public DatagramReady() {
	}
	
	public DatagramReady with(Executor executor) {
		this.executor = executor;
		return this;
	}

	public DatagramReady bind(Address bindAddress) {
		this.bindAddress = bindAddress;
		return this;
	}

	public Connectable create() {
		Connectable c = connectable;
		if (c != null) {
			c.disconnect();
		}
		final Address thisBindAddress = bindAddress;
		connectable = new SimpleConnectable(executor, new SimpleConnectable.Connect() {
			private DatagramChannel currentChannel = null;
			private SelectionKey currentSelectionKey = null;

			private final Deque<AddressedByteBuffer> toWriteQueue = new LinkedList<>();
			private long toWriteLength = 0L;

			@Override
			public void connect(final Connecting connecting, final Closing closing, final Failing failing, final Receiver receiver) {
				InternalQueue.post(new Runnable() {
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
								final SelectionKey selectionKey = channel.register(InternalQueue.selector, 0);
								currentSelectionKey = selectionKey;
								
								selectionKey.attach(new SelectionKeyVisitor() {
									@Override
									public void visit(final SelectionKey key) {
										if (!channel.isOpen()) {
											return;
										}
										if (key.isReadable()) {
											ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);
											InetSocketAddress from;
											try {
												from = (InetSocketAddress) channel.receive(readBuffer);
											} catch (IOException e) {
												LOGGER.trace("Connection failed", e);
												disconnect(channel, selectionKey);
												currentChannel = null;
												currentSelectionKey = null;
												if (closing != null) {
													closing.closed();
												}
												from = null;
											}
											if (from != null) {
												readBuffer.flip();
												if (receiver != null) {
													receiver.received(new Address(from.getHostString(), from.getPort()), readBuffer);
												}
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
														if (closing != null) {
															closing.closed();
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
			public void disconnect() {
				InternalQueue.post(new Runnable() {
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
				InternalQueue.post(new Runnable() {
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
						LOGGER.trace("Write buffer: {} bytes (current size: {} bytes)", b.buffer.remaining(), toWriteLength);
						
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
			}
		});
		return connectable;
	}
}
