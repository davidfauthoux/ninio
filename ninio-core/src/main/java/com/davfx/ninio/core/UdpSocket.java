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

public final class UdpSocket implements Connector {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(UdpSocket.class);

	private static final Config CONFIG = ConfigUtils.load(UdpSocket.class);
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("datagram.write.buffer").longValue();

	public static interface Builder extends ConfigurableNinioBuilder<Connector, Builder> {
		Builder with(ByteBufferAllocator byteBufferAllocator);
		Builder bind(Address bindAddress);
	}

	public static Builder builder() {
		return new Builder() {
			private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();
			
			private Address bindAddress = null;
			
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
			@Override
			public Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
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
			public Connector create(Queue queue) {
				return new UdpSocket(queue, byteBufferAllocator, bindAddress, connecting, closing, failing, receiver);
			}
		};
	}
	
	private static final class AddressedByteBuffer {
		Address address;
		ByteBuffer buffer;
	}

	private final Queue queue;
	private final Closing closing;
	private DatagramChannel currentChannel = null;
	private SelectionKey currentSelectionKey = null;

	private final Deque<AddressedByteBuffer> toWriteQueue = new LinkedList<>();
	private long toWriteLength = 0L;

	public UdpSocket(final Queue queue, final ByteBufferAllocator byteBufferAllocator, final Address bindAddress, final Connecting connecting, Closing closing, final Failing failing, final Receiver receiver) {
		this.queue = queue;
		this.closing = closing;

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
								if (!channel.isOpen()) {
									return;
								}
								
								if (key.isReadable()) {
									final ByteBuffer readBuffer = byteBufferAllocator.allocate();
									InetSocketAddress from;
									try {
										from = (InetSocketAddress) channel.receive(readBuffer);
									} catch (IOException e) {
										LOGGER.trace("Connection failed", e);
										disconnect(channel, selectionKey);
										currentChannel = null;
										currentSelectionKey = null;
										return;
									}

									readBuffer.flip();
									if (receiver != null) {
										Address a = new Address(from.getHostString(), from.getPort());
										receiver.received(UdpSocket.this, a, readBuffer);
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
												return;
											}
										} else {
											InetSocketAddress a;
											try {
												a = new InetSocketAddress(b.address.host, b.address.port); // Note this call blocks to resolve host (DNS resolution) //TODO Test unresolved
												if (a.isUnresolved()) {
													throw new IOException("Unresolved address: " + b.address);
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
						
						if (bindAddress != null) {
							try {
								InetSocketAddress a = new InetSocketAddress(bindAddress.host, bindAddress.port); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
								if (a.isUnresolved()) {
									throw new IOException("Unresolved address: " + bindAddress);
								}
								channel.socket().bind(a);
							} catch (IOException e) {
								disconnect(channel, selectionKey);
								throw new IOException("Could not bind to: " + bindAddress, e);
							}
						}
					} catch (IOException e) {
						disconnect(channel, null);
						throw e;
					}
				} catch (final IOException e) {
					if (failing != null) {
						failing.failed(e);
					}
					return;
				}

				if (connecting != null) {
					connecting.connected(UdpSocket.this, null);
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

		if (closing != null) {
			closing.closed();
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
		//%% queue.waitFor();
	}
	
	@Override
	public Connector send(final Address address, final ByteBuffer buffer) {
		queue.execute(new Runnable() {
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
}
