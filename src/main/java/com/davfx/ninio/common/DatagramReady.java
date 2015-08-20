package com.davfx.ninio.common;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class DatagramReady implements Ready {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(DatagramReady.class);
	
	private static final Config CONFIG = ConfigUtils.load(TcpdumpSyncDatagramReady.class);
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.read.size").intValue();
	private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.write.size").intValue();
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.write.buffer").longValue();

	private static final class AddressedByteBuffer {
		Address address;
		ByteBuffer buffer;
	}

	private final Selector selector;
	private final ByteBufferAllocator byteBufferAllocator;
	
	private boolean bind = false;

	public DatagramReady(Selector selector, ByteBufferAllocator byteBufferAllocator) {
		this.selector = selector;
		this.byteBufferAllocator = byteBufferAllocator;
	}
	
	public DatagramReady bind() {
		bind = true;
		return this;
	}
	
	@Override
	public void connect(Address address, final ReadyConnection connection) {
		try {
			final DatagramChannel channel = DatagramChannel.open();
			try {
				channel.configureBlocking(false);
				channel.socket().setReceiveBufferSize(READ_BUFFER_SIZE);
				channel.socket().setSendBufferSize(WRITE_BUFFER_SIZE);
				final SelectionKey selectionKey = channel.register(selector, 0);
				
				final LinkedList<AddressedByteBuffer> toWriteQueue = new LinkedList<>();
				final long[] toWriteLength = new long[] { 0L };
	
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
								if (from == null) {
									try {
										channel.close();
									} catch (IOException ee) {
									}
									connection.close();
								} else {
									readBuffer.flip();
									connection.handle(new Address(from.getHostString(), from.getPort()), readBuffer);
								}
							} catch (IOException e) {
								try {
									channel.close();
								} catch (IOException ee) {
								}
								connection.close();
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
									if (b.address == null) {
										try {
											channel.write(b.buffer);
										} catch (IOException e) {
											try {
												channel.close();
											} catch (IOException ee) {
											}
											connection.close();
											break;
										}
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
												toWriteLength[0] -= before - b.buffer.remaining();
											} catch (IOException e) {
												try {
													channel.close();
												} catch (IOException ee) {
												}
												connection.close();
												break;
											}
										}
									}
									
									if (b.buffer.hasRemaining()) {
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
	
				connection.connected(new FailableCloseableByteBufferHandler() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
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
						toWriteLength[0] += b.buffer.remaining();
						while ((WRITE_MAX_BUFFER_SIZE > 0L) && (toWriteLength[0] > WRITE_MAX_BUFFER_SIZE)) {
							AddressedByteBuffer r = toWriteQueue.removeFirst();
							long l = r.buffer.remaining();
							toWriteLength[0] -= l;
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
				});
			} catch (IOException e) {
				try {
					channel.close();
				} catch (IOException ee) {
				}
				throw e;
			}
		} catch (IOException e) {
			connection.failed(e);
		}
	}
}
