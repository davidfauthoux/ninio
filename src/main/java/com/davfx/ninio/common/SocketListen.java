package com.davfx.ninio.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SocketListen implements Listen {
	private static final Logger LOGGER = LoggerFactory.getLogger(SocketListen.class);

	private final Selector selector;
	private final ByteBufferAllocator byteBufferAllocator;
	
	public SocketListen(Selector selector, ByteBufferAllocator byteBufferAllocator) {
		this.byteBufferAllocator = byteBufferAllocator;
		this.selector = selector;
	}
	
	@Override
	public void listen(final Address address, final SocketListening listening) {
		try {
			final ServerSocketChannel serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);
			// serverChannel.socket().setReceiveBufferSize();
			serverChannel.socket().bind(AddressUtils.toBindableInetSocketAddress(address));
			SelectionKey acceptSelectionKey = serverChannel.register(selector, 0);

			acceptSelectionKey.attach(new SelectionKeyVisitor() {
				@Override
				public void visit(SelectionKey key) {
					if (!key.isAcceptable()) {
						return;
					}
					ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
					try {
						final SocketChannel outboundChannel = ssc.accept();
						try {
							outboundChannel.configureBlocking(false);
							// not so useful because it's 2 hours timeout :(
							outboundChannel.socket().setKeepAlive(true);
							final Address clientAddress = new Address(outboundChannel.socket().getInetAddress().getHostAddress(), outboundChannel.socket().getPort());
							outboundChannel.finishConnect();
							final SelectionKey selectionKey = outboundChannel.register(selector, 0);
							
							final LinkedList<ByteBuffer> toWriteQueue = new LinkedList<ByteBuffer>();
							
							final CloseableByteBufferHandler read = listening.connected(clientAddress, new CloseableByteBufferHandler() {
								@Override
								public void handle(Address address, ByteBuffer buffer) {
									if (!outboundChannel.isOpen()) {
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
									if (!outboundChannel.isOpen()) {
										return;
									}
									if (!selectionKey.isValid()) {
										return;
									}
									toWriteQueue.addLast(null);
									selectionKey.interestOps(selectionKey.interestOps() | SelectionKey.OP_WRITE);
								}
							});
							
							selectionKey.attach(new SelectionKeyVisitor() {
								@Override
								public void visit(SelectionKey key) {
									if (!outboundChannel.isOpen()) {
										return;
									}
									if (key.isReadable()) {
										ByteBuffer readBuffer = byteBufferAllocator.allocate();
										try {
											int r = outboundChannel.read(readBuffer);
											if (r < 0) {
												LOGGER.debug("Closing client socket");
												try {
													outboundChannel.close();
												} catch (IOException ee) {
												}
												read.close();
											} else {
												// LOGGER.debug("Received packet of {} bytes", r);
												readBuffer.flip();
												read.handle(clientAddress, readBuffer);
											}
										} catch (IOException e) {
											LOGGER.debug("Error on client socket", e);
											try {
												outboundChannel.close();
											} catch (IOException ee) {
											}
											read.close();
										}
									} else if (key.isWritable()) {
										while (!toWriteQueue.isEmpty()) {
											ByteBuffer b = toWriteQueue.getFirst();
											if (b == null) {
												try {
													outboundChannel.close();
												} catch (IOException ee) {
												}
												return;
											} else {
												// LOGGER.debug("Sending packet of {} bytes", b.remaining());
												try {
													outboundChannel.write(b);
												} catch (IOException e) {
													try {
														outboundChannel.close();
													} catch (IOException ee) {
													}
													read.close();
													return;
												}
												
												if (b.hasRemaining()) {
													return;
												}
												
												toWriteQueue.removeFirst();
											}
										}
										if (!outboundChannel.isOpen()) {
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
						} catch (IOException e) {
							try {
								outboundChannel.close();
							} catch (IOException ee) {
							}
						}
					} catch (IOException e) {
						try {
							ssc.close();
						} catch (IOException ee) {
						}
					}
				}
			});
			
			acceptSelectionKey.interestOps(acceptSelectionKey.interestOps() | SelectionKey.OP_ACCEPT);

		} catch (ClosedSelectorException e) {
			listening.failed(new IOException(e));
		} catch (IOException e) {
			listening.failed(e);
		}
	}
}
