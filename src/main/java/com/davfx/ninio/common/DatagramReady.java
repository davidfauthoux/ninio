package com.davfx.ninio.common;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.LinkedList;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class DatagramReady implements Ready {
	private static final Config CONFIG = ConfigUtils.load(TcpdumpSyncDatagramReady.class);
	private static final int READ_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.read.size").intValue();
	private static final int WRITE_BUFFER_SIZE = CONFIG.getBytes("ninio.datagram.write.size").intValue();

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
									try {
										if (b.address == null) {
											channel.write(b.buffer);
										} else {
											channel.send(b.buffer, AddressUtils.toConnectableInetSocketAddress(b.address));
										}
									} catch (IOException e) {
										try {
											channel.close();
										} catch (IOException ee) {
										}
										connection.close();
										break;
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
