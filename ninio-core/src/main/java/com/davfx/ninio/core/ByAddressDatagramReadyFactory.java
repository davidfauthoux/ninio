package com.davfx.ninio.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ByAddressDatagramReadyFactory implements ReadyFactory, AutoCloseable, Closeable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ByAddressDatagramReadyFactory.class);

	private static final Config CONFIG = ConfigFactory.load(ByAddressDatagramReadyFactory.class.getClassLoader());
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.read.size").intValue();
	private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.write.size").intValue();
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.write.buffer").longValue();
	
	private static final class AddressedByteBuffer {
		Address address;
		ByteBuffer buffer;
	}
	
	private final Queue queue;
	private final Map<Address, ReadyConnection> connections = new HashMap<>();
	private FailableCloseableByteBufferHandler write = null;
	private IOException error = null;
	private boolean closed = false;
	private final List<ReadyConnection> toConnect = new LinkedList<>();
	
	public ByAddressDatagramReadyFactory(Queue globalQueue) {
		queue = globalQueue;
		final Selector selector = queue.getSelector();
		final ByteBufferAllocator byteBufferAllocator = queue.allocator();
		queue.post(new Runnable() {
			private final LinkedList<AddressedByteBuffer> toWriteQueue = new LinkedList<>();
			private long toWriteLength = 0L;
			@Override
			public void run() {
				final DatagramChannel channel;
				final SelectionKey selectionKey;
				try {
					channel = DatagramChannel.open();
					try {
						channel.configureBlocking(false);
						channel.socket().setReceiveBufferSize(READ_BUFFER_SIZE);
						channel.socket().setSendBufferSize(WRITE_BUFFER_SIZE);
						selectionKey = channel.register(selector, 0);
						
						selectionKey.attach(new SelectionKeyVisitor() {
							@Override
							public void visit(final SelectionKey key) {
								if (!channel.isOpen()) {
									return;
								}
								if (key.isReadable()) {
									ByteBuffer readBuffer = byteBufferAllocator.allocate();
									try {
										InetSocketAddress from = (InetSocketAddress) channel.receive(readBuffer);
										if (from != null) {
											readBuffer.flip();
											Address fromAddress = new Address(from.getHostString(), from.getPort());
											ReadyConnection connection = connections.get(fromAddress);
											if (connection != null) {
												connection.handle(fromAddress, readBuffer);
											} else {
												LOGGER.trace("Packet received but no match: {} / {}", fromAddress, connections.keySet());
											}
										}
									} catch (IOException e) {
										try {
											channel.close();
										} catch (IOException ee) {
										}
										closed = true;
										for (ReadyConnection connection : connections.values()) {
											connection.close();
										}
										connections.clear();
									}
								} else if (key.isWritable()) {
									while (!toWriteQueue.isEmpty()) {
										AddressedByteBuffer b = toWriteQueue.getFirst();
										if (b == null) {
											try {
												channel.close();
											} catch (IOException ee) {
											}
											return;
										} else {
											InetSocketAddress a;
											try {
												a = AddressUtils.toConnectableInetSocketAddress(b.address);
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
													LOGGER.debug("Error trying to send to {}", b.address, e);
													b = null;
												}
											}
											
											if ((b != null) && b.buffer.hasRemaining()) {
												return;
											}
											
											toWriteQueue.removeFirst();
										}
									}
									if (!selector.isOpen()) {
										return;
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
						
						/*%%
						try {
							if (bind) {
								InetSocketAddress a = AddressUtils.toBindableInetSocketAddress(address);
								if (a == null) {
									throw new IOException("Invalid address");
								}
								channel.socket().bind(a);
							} else {
								InetSocketAddress a = AddressUtils.toConnectableInetSocketAddress(address);
								if (a == null) {
									throw new IOException("Invalid address");
								}
								channel.connect(a);
							}
						} catch (IOException e) {
							selectionKey.cancel();
							throw e;
						}
						*/
			
					} catch (IOException e) {
						try {
							channel.close();
						} catch (IOException ee) {
						}
						throw e;
					}
				} catch (IOException e) {
					write = null;
					error = e;
					for (ReadyConnection connection : toConnect) {
						connection.failed(e);
					}
					toConnect.clear();
					return;
				}
				
				write = new FailableCloseableByteBufferHandler() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						if (address == null) {
							LOGGER.warn("Packet address is null, dropping");
							return;
						}
						//%% if (address.getHost().equals("null")) {
						//%% return;
						//%% }

						if (!selector.isOpen()) {
							return;
						}
						if (!channel.isOpen()) {
							return;
						}
						if (!selectionKey.isValid()) {
							return;
						}
						
						AddressedByteBuffer b = new AddressedByteBuffer();
						b.address = address;
						b.buffer = buffer;
						toWriteQueue.addLast(b);
						toWriteLength += b.buffer.remaining();
						while ((WRITE_MAX_BUFFER_SIZE > 0L) && (toWriteLength > WRITE_MAX_BUFFER_SIZE)) {
							AddressedByteBuffer r = toWriteQueue.removeFirst();
							long l = r.buffer.remaining();
							toWriteLength -= l;
							LOGGER.warn("Dropping {} bytes that should have been sent to {}", l, r.address);
						}
						selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
					}
					@Override
					public void close() {
						if (!selector.isOpen()) {
							return;
						}
						if (!channel.isOpen()) {
							return;
						}
						if (!selectionKey.isValid()) {
							return;
						}
						toWriteQueue.addLast(null);
						selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
					}
					@Override
					public void failed(IOException e) {
						close();
					}
				};

				for (ReadyConnection connection : toConnect) {
					connection.connected(write);
				}
				toConnect.clear();
			}
		});
	}
	
	@Override
	public Ready create() {
		return new QueueReady(queue, new Ready() {
			@Override
			public void connect(final Address address, ReadyConnection connection) {
				//%% queue.check();
				
				// Address ignored, socket not connected, not bound
				
				if (error != null) {
					connection.failed(error);
					return;
				}
				
				if (closed) {
					connection.close();
					return;
				}
				
				if (write == null) {
					toConnect.add(connection);
					return;
				}
				
				connections.put(address, connection);
				connection.connected(new FailableCloseableByteBufferHandler() {
					@Override
					public void failed(IOException e) {
						//%% queue.check();
						
						connections.remove(address);

						/* Global connection never closed
						write.failed(e);
						*/
					}
					@Override
					public void close() {
						//%% queue.check();
						
						connections.remove(address);

						/* Global connection never closed
						write.close();
						*/
					}
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						//%% queue.check();
						
						write.handle(address, buffer);
					}
				});
			}
		});
	}
	
	@Override
	public void close() {
		queue.post(new Runnable() {
			@Override
			public void run() {
				for (ReadyConnection connection : toConnect) {
					connection.close();
				}
				toConnect.clear();
				for (ReadyConnection c : connections.values()) {
					c.close();
				}
				connections.clear();
			}
		});
	}
}
