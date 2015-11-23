package com.davfx.ninio.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class SocketReady implements Ready {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SocketReady.class);

	private static final Config CONFIG = ConfigFactory.load(SocketReady.class.getClassLoader());
	// private static final double TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.socket.timeout");
	private static final long WRITE_MAX_BUFFER_SIZE = CONFIG.getBytes("ninio.socket.write.buffer").longValue();

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
				// channel.socket().setSoTimeout((int) (TIMEOUT * 1000d)); // Not working with NIO
				channel.configureBlocking(false);
				SelectionKey inboundKey = channel.register(selector, SelectionKey.OP_CONNECT);
				inboundKey.attach(new SelectionKeyVisitor() {
					private long toWriteLength = 0L;

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
												/*%%%
												while (readBuffer.hasRemaining()) {
													byte[] bbb = new byte[] { readBuffer.get() };
													ByteBuffer b = ByteBuffer.wrap(bbb);
													connection.handle(address, b);
												}
												*/
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
												long before = b.remaining();
												try {
													channel.write(b);
													toWriteLength -= before - b.remaining();
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
									if (toWriteLength < 0L) {
										return;
									}
									toWriteQueue.addLast(buffer);
									toWriteLength += buffer.remaining();
									while ((WRITE_MAX_BUFFER_SIZE > 0L) && (toWriteLength > WRITE_MAX_BUFFER_SIZE)) {
										LOGGER.warn("Buffer overflow, closing connection to {}", address);
										toWriteLength = -1L;
										toWriteQueue.clear();
										toWriteQueue.addLast(null);
										return;
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
									if (toWriteLength < 0L) {
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
