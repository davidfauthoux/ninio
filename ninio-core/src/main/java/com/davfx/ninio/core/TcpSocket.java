package com.davfx.ninio.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

public final class TcpSocket implements Connecter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpSocket.class);

	private static final Config CONFIG = ConfigUtils.load(TcpSocket.class);
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("socket.write.buffer").longValue();
	// private static final double TIMEOUT = ConfigUtils.getDuration(CONFIG, "socket.timeout");

	public static interface Builder extends NinioBuilder<TcpSocket> {
		Builder with(ByteBufferAllocator byteBufferAllocator);
		Builder bind(Address bindAddress);
		Builder to(Address connectAddress);
	}

	public static Builder builder() {
		return new Builder() {
			private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();
			
			private Address bindAddress = null;
			private Address connectAddress = null;
			
			@Override
			public Builder with(ByteBufferAllocator byteBufferAllocator) {
				this.byteBufferAllocator = byteBufferAllocator;
				return this;
			}

			@Override
			public Builder bind(Address bindAddress) {
				this.bindAddress = bindAddress;
				return this;
			}
			
			@Override
			public Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public TcpSocket create(Queue queue) {
				return new TcpSocket(queue, byteBufferAllocator, bindAddress, connectAddress);
			}
		};
	}
	
	private static final class ToWrite {
		public final ByteBuffer buffer;
		public final Connecter.Connecting.Callback callback;
		public ToWrite(ByteBuffer buffer, Connecter.Connecting.Callback callback) {
			this.buffer = buffer;
			this.callback = callback;
		}
	}
	
	private final Queue queue;
	private final ByteBufferAllocator byteBufferAllocator;
	private final Address bindAddress;
	private final Address connectAddress;
	
	private SocketChannel currentChannel = null;
	private SelectionKey currentInboundKey = null;
	private SelectionKey currentSelectionKey = null;

	private final Deque<ToWrite> toWriteQueue = new LinkedList<>();
	private long toWriteLength = 0L;
	
	private boolean closed = false;

	private TcpSocket(Queue queue, ByteBufferAllocator byteBufferAllocator, Address bindAddress, Address connectAddress) {
		this.queue = queue;
		this.byteBufferAllocator = byteBufferAllocator;
		this.bindAddress = bindAddress;
		this.connectAddress = connectAddress;
	}
	
	@Override
	public Connecting connect(final Callback callback) {
		queue.execute(new Runnable() {
			@Override
			public void run() {
				try {
					final SocketChannel channel = SocketChannel.open();
					currentChannel = channel;
					try {
						// channel.socket().setSoTimeout((int) (TIMEOUT * 1000d)); // Not working with NIO
						channel.configureBlocking(false);
						final SelectionKey inboundKey = queue.register(channel);
						inboundKey.interestOps(inboundKey.interestOps() | SelectionKey.OP_CONNECT);
						currentInboundKey = inboundKey;
						inboundKey.attach(new SelectionKeyVisitor() {
							@Override
							public void visit(SelectionKey key) {
								if (closed) {
									disconnect(channel, inboundKey, null, callback);
									return;
								}
								
								if (!key.isConnectable()) {
									return;
								}
				
								try {
									channel.finishConnect();
									final SelectionKey selectionKey = queue.register(channel);
									currentSelectionKey = selectionKey;
		
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
														disconnect(channel, inboundKey, selectionKey, callback);
														currentChannel = null;
														currentInboundKey = null;
														currentSelectionKey = null;
														return;
													}
												} catch (IOException e) {
													LOGGER.trace("Connection failed", e);
													disconnect(channel, inboundKey, selectionKey, callback);
													return;
												}

												readBuffer.flip();
												callback.received(null, readBuffer);
											} else if (key.isWritable()) {
												while (true) {
													ToWrite toWrite = toWriteQueue.peek();
													if (toWrite == null) {
														break;
													}
													long before = toWrite.buffer.remaining();
													try {
														LOGGER.trace("Actual write buffer: {} bytes", toWrite.buffer.remaining());
														channel.write(toWrite.buffer);
														toWriteLength -= before - toWrite.buffer.remaining();
													} catch (IOException e) {
														LOGGER.trace("Connection failed", e);
														disconnect(channel, inboundKey, selectionKey, callback);
														return;
													}
													
													if (toWrite.buffer.hasRemaining()) {
														return;
													}
													
													toWrite.callback.sent();
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
		
									callback.connected(null);
									
								} catch (final IOException e) {
									LOGGER.trace("Connection failed", e);
									disconnect(channel, inboundKey, null, callback);
									callback.failed(e);
								}
							}
						});
						
						if (bindAddress != null) {
							try {
								InetSocketAddress a = new InetSocketAddress(bindAddress.host, bindAddress.port); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
								if (a.isUnresolved()) {
									throw new IOException("Unresolved address: " + bindAddress);
								}
								channel.socket().bind(a);
							} catch (IOException e) {
								disconnect(channel, inboundKey, null, null);
								throw new IOException("Could not bind to: " + bindAddress, e);
							}
						}

						try {
							InetSocketAddress a = new InetSocketAddress(connectAddress.host, connectAddress.port); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
							if (a.isUnresolved()) {
								throw new IOException("Unresolved address: " + connectAddress);
							}
							LOGGER.trace("Connecting to: {}", a);
							channel.connect(a);
						} catch (IOException e) {
							disconnect(channel, inboundKey, null, null);
							throw new IOException("Could not connect to: " + connectAddress, e);
						}
					} catch (IOException e) {
						disconnect(channel, null, null, null);
						throw e;
					}
		
				} catch (IOException ee) {
					callback.failed(ee);
					return;
				}
			}
		});
		
		return new Connecting() {
			@Override
			public void close() {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						disconnect(currentChannel, currentInboundKey, currentSelectionKey, callback);
					}
				});
			}
			
			@Override
			public void send(final Address address, final ByteBuffer buffer, final Callback callback) {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						if (closed) {
							callback.failed(new IOException("Closed"));
							return;
						}

						if (address != null) {
							LOGGER.warn("Ignored send address: {}", address);
						}
						
						if ((WRITE_MAX_BUFFER_SIZE > 0L) && (toWriteLength > WRITE_MAX_BUFFER_SIZE)) {
							LOGGER.warn("Dropping {} bytes that should have been sent to {}", buffer.remaining(), address);
							callback.failed(new IOException("Packet dropped"));
							return;
						}
						
						toWriteQueue.add(new ToWrite(buffer, callback));
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
		};
	}

	private void disconnect(SocketChannel channel, SelectionKey inboundKey, SelectionKey selectionKey, Connecter.Callback callback) {
		if (channel != null) {
			try {
				channel.socket().close();
			} catch (IOException e) {
			}
			try {
				channel.close();
			} catch (IOException e) {
			}
		}
		if (inboundKey != null) {
			inboundKey.cancel();
		}
		if (selectionKey != null) {
			selectionKey.cancel();
		}

		IOException e = new IOException("Closed");
		for (ToWrite toWrite : toWriteQueue) {
			toWrite.callback.failed(e);
		}

		currentChannel = null;
		currentInboundKey = null;
		currentSelectionKey = null;
		closed = true;

		if (callback != null) {
			callback.closed();
		}
	}

}
