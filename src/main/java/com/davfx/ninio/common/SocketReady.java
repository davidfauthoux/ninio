package com.davfx.ninio.common;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

public final class SocketReady implements Ready {
	private final Selector selector;
	private final ByteBufferAllocator byteBufferAllocator;
	
	public SocketReady(Selector selector, ByteBufferAllocator byteBufferAllocator) {
		this.selector = selector;
		this.byteBufferAllocator = byteBufferAllocator;
	}
	
	@Override
	public void connect(final Address address, final ReadyConnection connection) {
		try {
			final SocketChannel channel = SocketChannel.open();
			try {
				channel.configureBlocking(false);
				SelectionKey inboundKey = channel.register(selector, SelectionKey.OP_CONNECT);
				inboundKey.attach(new SelectionKeyVisitor() {
					@Override
					public void visit(SelectionKey key) {
						if (!key.isConnectable()) {
							return;
						}
		
						try {
							channel.finishConnect();
							final SelectionKey selectionKey = channel.register(selector, 0);
							
							final LinkedList<ByteBuffer> toWriteQueue = new LinkedList<ByteBuffer>();
	
							selectionKey.attach(new SelectionKeyVisitor() {
								@Override
								public void visit(SelectionKey key) {
									if (!channel.isOpen()) {
										return;
									}
									if (key.isReadable()) {
										ByteBuffer readBuffer = byteBufferAllocator.allocate();
										try {
											if (channel.read(readBuffer) < 0) {
												try {
													channel.close();
												} catch (IOException ee) {
												}
												connection.close();
											} else {
												readBuffer.flip();
												connection.handle(address, readBuffer);
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
											ByteBuffer b = toWriteQueue.getFirst();
											if (b == null) {
												try {
													channel.close();
												} catch (IOException ee) {
												}
												return;
											} else {
												try {
													channel.write(b);
												} catch (IOException e) {
													try {
														channel.close();
													} catch (IOException ee) {
													}
													connection.close();
													return;
												}
												
												if (b.hasRemaining()) {
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
									toWriteQueue.addLast(buffer);
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
							connection.failed(e);
						}
					}
				});
				
				try {
					InetSocketAddress a = AddressUtils.toConnectableInetSocketAddress(address);
					if (a == null) {
						throw new IOException("Invalid address");
					}
					channel.connect(a);
				} catch (IOException e) {
					inboundKey.cancel();
					throw e;
				}
			} catch (IOException e) {
				try {
					channel.close();
				} catch (IOException ee) {
				}
				throw e;
			}

		} catch (ClosedSelectorException e) {
			connection.failed(new IOException(e));
		} catch (IOException e) {
			connection.failed(e);
		}
	}
}
