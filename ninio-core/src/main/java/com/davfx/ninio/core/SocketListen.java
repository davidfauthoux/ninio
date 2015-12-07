package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.SocketListening.Listening;

public final class SocketListen implements Listen {
	private static final Logger LOGGER = LoggerFactory.getLogger(SocketListen.class);

	private final Selector selector;
	private final ByteBufferAllocator byteBufferAllocator;
	private final Set<SocketChannel> outboundChannels = new HashSet<>();
	
	public SocketListen(Selector selector, ByteBufferAllocator byteBufferAllocator) {
		this.byteBufferAllocator = byteBufferAllocator;
		this.selector = selector;
	}
	
	private void accepted(SocketChannel outboundChannel) {
		outboundChannels.add(outboundChannel);
		LOGGER.debug("-> Clients connected: {}", outboundChannels.size());
	}
	private void removed(SocketChannel outboundChannel) {
		try {
			outboundChannel.close();
		} catch (IOException ee) {
		}
		outboundChannels.remove(outboundChannel);
		LOGGER.debug("<- Clients connected: {}", outboundChannels.size());
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
						accepted(outboundChannel);
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
												LOGGER.debug("Client socket closed by peer");
												removed(outboundChannel);
												read.close();
											} else {
												// LOGGER.debug("Received packet of {} bytes", r);
												readBuffer.flip();
												read.handle(clientAddress, readBuffer);
											}
										} catch (IOException e) {
											LOGGER.debug("Error on client socket", e);
											removed(outboundChannel);
											read.close();
										}
									} else if (key.isWritable()) {
										while (!toWriteQueue.isEmpty()) {
											ByteBuffer b = toWriteQueue.getFirst();
											if (b == null) {
												removed(outboundChannel);
												return;
											} else {
												// LOGGER.debug("Sending packet of {} bytes", b.remaining());
												try {
													outboundChannel.write(b);
												} catch (IOException e) {
													removed(outboundChannel);
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
							removed(outboundChannel);
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

			listening.listening(new Listening() {
				@Override
				public void disconnect() {
					LOGGER.debug("Disconnecting clients");
					for (SocketChannel s : outboundChannels) {
						try {
							s.close();
						} catch (IOException e) {
						}
					}
					outboundChannels.clear();
				}
				@Override
				public void close() {
					LOGGER.debug("Server socket closed");
					try {
						serverChannel.close();
					} catch (IOException e) {
					}
				}
			});

		} catch (ClosedSelectorException e) {
			listening.failed(new IOException(e));
		} catch (IOException e) {
			listening.failed(e);
		}
	}
}
