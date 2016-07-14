package com.davfx.ninio.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Deque;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

public final class UdpSocket implements Connecter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(UdpSocket.class);

	private static final Config CONFIG = ConfigUtils.load(UdpSocket.class);
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("datagram.write.buffer").longValue();

	public static interface Builder extends NinioBuilder<UdpSocket> {
		Builder with(ByteBufferAllocator byteBufferAllocator);
		Builder bind(Address bindAddress);
	}

	public static Builder builder() {
		return new Builder() {
			private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();
			
			private Address bindAddress = null;
			
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
			public UdpSocket create(Queue queue) {
				return new UdpSocket(queue, byteBufferAllocator, bindAddress);
			}
		};
	}
	
	private static final class ToWrite {
		public final Address address;
		public final ByteBuffer buffer;
		public final Connecter.Connecting.Callback callback;
		public ToWrite(Address address, ByteBuffer buffer, Connecter.Connecting.Callback callback) {
			this.address = address;
			this.buffer = buffer;
			this.callback = callback;
		}
	}

	private final Queue queue;
	private final ByteBufferAllocator byteBufferAllocator;
	private final Address bindAddress;
	private DatagramChannel currentChannel = null;
	private SelectionKey currentSelectionKey = null;

	private final Deque<ToWrite> toWriteQueue = new LinkedList<>();
	private long toWriteLength = 0L;

	private boolean closed = false;

	public UdpSocket(Queue queue, ByteBufferAllocator byteBufferAllocator, Address bindAddress) {
		this.queue = queue;
		this.byteBufferAllocator = byteBufferAllocator;
		this.bindAddress = bindAddress;
	}
	
	@Override
	public Connecter.Connecting connect(final Connecter.Callback callback) {
		queue.execute(new Runnable() {
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
						//%% channel.socket().setReceiveBufferSize(READ_BUFFER_SIZE);
						//%% channel.socket().setSendBufferSize(WRITE_BUFFER_SIZE);
						final SelectionKey selectionKey = queue.register(channel);
						currentSelectionKey = selectionKey;
						
						selectionKey.attach(new SelectionKeyVisitor() {
							@Override
							public void visit(final SelectionKey key) {
								if (closed) {
									disconnect(channel, selectionKey, callback);
									return;
								}

								if (!channel.isOpen()) {
									return;
								}
								
								if (key.isReadable()) {
									ByteBuffer readBuffer = byteBufferAllocator.allocate();
									InetSocketAddress from;
									try {
										from = (InetSocketAddress) channel.receive(readBuffer);
									} catch (IOException e) {
										LOGGER.trace("Read failed", e);
										disconnect(channel, selectionKey, callback);
										return;
									}

									readBuffer.flip();
									Address a = new Address(from.getHostString(), from.getPort());
									callback.received(a, readBuffer);
								} else if (key.isWritable()) {
									while (true) {
										ToWrite toWrite = toWriteQueue.peek();
										if (toWrite == null) {
											break;
										}
										if (toWrite.address == null) {
											try {
												LOGGER.trace("Actual write buffer: {} bytes", toWrite.buffer.remaining());
												channel.write(toWrite.buffer);
											} catch (IOException e) {
												LOGGER.trace("Write failed", e);
												disconnect(channel, selectionKey, callback);
												return;
											}
										} else {
											InetSocketAddress a;
											try {
												a = new InetSocketAddress(toWrite.address.host, toWrite.address.port); // Note this call blocks to resolve host (DNS resolution) //TODO Test unresolved
												if (a.isUnresolved()) {
													throw new IOException("Unresolved address: " + toWrite.address);
												}
											} catch (IOException e) {
												LOGGER.warn("Invalid address: {}", toWrite.address);
												LOGGER.trace("Write failed", e);
												disconnect(channel, selectionKey, callback);
												return;
											}
											
											long before = toWrite.buffer.remaining();
											try {
												LOGGER.trace("Actual write buffer: {} bytes", toWrite.buffer.remaining());
												channel.send(toWrite.buffer, a);
												toWriteLength -= before - toWrite.buffer.remaining();
											} catch (IOException e) {
												LOGGER.trace("Write failed", e);
												disconnect(channel, selectionKey, callback);
												return;
											}
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
						
						if (bindAddress != null) {
							try {
								InetSocketAddress a = new InetSocketAddress(bindAddress.host, bindAddress.port); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
								if (a.isUnresolved()) {
									throw new IOException("Unresolved address: " + bindAddress);
								}
								channel.socket().bind(a);
							} catch (IOException e) {
								disconnect(channel, selectionKey, callback);
								throw new IOException("Could not bind to: " + bindAddress, e);
							}
						}
					} catch (IOException e) {
						disconnect(channel, null, callback);
						throw e;
					}
				} catch (IOException e) {
					callback.failed(e);
					return;
				}

				callback.connected(null);
			}
		});
		
		return new Connecting() {
			@Override
			public void close() {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						disconnect(currentChannel, currentSelectionKey, callback);
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

						if ((WRITE_MAX_BUFFER_SIZE > 0L) && (toWriteLength > WRITE_MAX_BUFFER_SIZE)) {
							LOGGER.warn("Dropping {} bytes that should have been sent to {}", buffer.remaining(), address);
							callback.failed(new IOException("Packet dropped"));
							return;
						}
						
						toWriteQueue.add(new ToWrite(address, buffer, callback));
						toWriteLength += buffer.remaining();
						LOGGER.trace("Write buffer: {} bytes (to {}) (current size: {} bytes)", buffer.remaining(), address, toWriteLength);
						
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
		};
	}
			
	private void disconnect(DatagramChannel channel, SelectionKey selectionKey, Connecter.Callback callback) {
		if (channel != null) {
			channel.socket().close();
			try {
				channel.close();
			} catch (IOException e) {
			}
		}
		if (selectionKey != null) {
			selectionKey.cancel();
		}

		IOException e = new IOException("Closed");
		for (ToWrite toWrite : toWriteQueue) {
			toWrite.callback.failed(e);
		}

		currentChannel = null;
		currentSelectionKey = null;

		if (!closed) {
			closed = true;
	
			if (callback != null) {
				callback.closed();
			}
		}
	}
}
