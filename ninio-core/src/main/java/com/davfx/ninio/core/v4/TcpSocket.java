package com.davfx.ninio.core.v4;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

public final class TcpSocket {

	private static final Logger LOGGER = LoggerFactory.getLogger(TcpSocket.class);
	
	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(com.davfx.ninio.core.TcpSocket.class.getPackage().getName());
	private static final double SOCKET_TIMEOUT = ConfigUtils.getDuration(CONFIG, "tcp.socket.timeout");
	private static final long SOCKET_WRITE_BUFFER_SIZE = CONFIG.getBytes("tcp.socket.write").longValue();
	private static final long SOCKET_READ_BUFFER_SIZE = CONFIG.getBytes("tcp.socket.read").longValue();

	private static final Supervision.Supervise SUPERVISION = Supervision.supervise("tcp");

	private final Queue queue;
	private final SocketChannelProvider channelProvider;
	
	private Address bindAddress = null;
	
	private boolean closed = false;
	private SocketChannel currentChannel = null;
	private SelectionKey currentInboundKey = null;
	private SelectionKey currentSelectionKey = null;
	
	private static final class Reading {
		public final CompletableFuture<Void> future;
		public final MutableByteArray buffer;
		public int index = 0;
		public int offset = 0;
		public Reading(CompletableFuture<Void> future, MutableByteArray buffer) {
			this.future = future;
			this.buffer = buffer;
		}
	}
	
	private static final class Writing {
		public final CompletableFuture<Void> future;
		public final ByteArray buffer;
		public Writing(CompletableFuture<Void> future, ByteArray buffer) {
			this.future = future;
			this.buffer = buffer;
		}
	}
	
	private final Deque<Reading> readings = new LinkedList<>();
	private final Deque<Writing> writings = new LinkedList<>();
	// private final Deque<CompletableFuture<Void>> onCloses = new LinkedList<>();
	
	TcpSocket(Queue queue, SocketChannelProvider channelProvider) {
		this.queue = queue;
		this.channelProvider = channelProvider;
	}
	
	public TcpSocket(Ninio ninio) {
		this(ninio.register(NinioPriority.REGULAR), new SocketChannelProvider() {
			@Override
			public SocketChannel open() throws IOException {
				return SocketChannel.open();
			}
		});
	}
	
	/*
	public CompletableFuture<Void> onClose() {
		CompletableFuture<Void> future = new CompletableFuture<>();
		queue.execute(new Runnable() {
			@Override
			public void run() {
				if (closed) {
					future.complete(null);
					return;
				}
				onCloses.addLast(future);
			}
		});
		return future;
	}
	*/
	
	public TcpSocket bind(Address bindAddress) {
		this.bindAddress = bindAddress;
		return this;
	}
	
	public CompletableFuture<Void> connect(Address connectAddress) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		queue.execute(new Runnable() {
			@Override
			public void run() {
				try {
					if (closed) {
						throw new IOException("Closed");
					}
					if (currentChannel != null) {
						throw new IOException("Already connected");
					}

					final SocketChannel channel = channelProvider.open(); // SocketChannel.open();
					currentChannel = channel;
					try {
						channel.configureBlocking(false);
						if (SOCKET_TIMEOUT > 0d) {
							channel.socket().setSoTimeout((int) (SOCKET_TIMEOUT * 1000d)); // Not working with NIO?
						}
						if (SOCKET_READ_BUFFER_SIZE > 0L) {
							channel.socket().setReceiveBufferSize((int) SOCKET_READ_BUFFER_SIZE);
						}
						if (SOCKET_WRITE_BUFFER_SIZE > 0L) {
							channel.socket().setSendBufferSize((int) SOCKET_WRITE_BUFFER_SIZE);
						}
						
						final SelectionKey inboundKey;
						if (connectAddress != null) {
							inboundKey = queue.register(channel);
							inboundKey.interestOps(inboundKey.interestOps() | SelectionKey.OP_CONNECT);
							currentInboundKey = inboundKey;
							inboundKey.attach(new SelectionKeyVisitor() {
								@Override
								public void visit(SelectionKey key) {
									if (closed) {
										disconnect(channel, inboundKey, null, null);
										return;
									}
									
									if (!key.isConnectable()) {
										return;
									}
					
									try {
										channel.finishConnect();
										init(channel, inboundKey);
									} catch (IOException e) {
										disconnect(channel, inboundKey, null, e);
										future.completeExceptionally(new IOException("Connection failed", e));
										return;
									}
		
									future.complete(null);
								}
							});
						} else {
							inboundKey = null;
							init(channel, inboundKey);
						}
	
						if (bindAddress != null) {
							try {
								channel.socket().bind(new InetSocketAddress(InetAddress.getByAddress(bindAddress.ip), bindAddress.port));
							} catch (IOException e) {
								disconnect(channel, inboundKey, null, e);
								throw new IOException("Could not bind to: " + bindAddress, e);
							}
						}
	
						if (connectAddress != null) {
							try {
								InetSocketAddress a = new InetSocketAddress(InetAddress.getByAddress(connectAddress.ip), connectAddress.port);
								LOGGER.trace("Connecting to: {}", a);
								channel.connect(a);
							} catch (IOException e) {
								disconnect(channel, inboundKey, null, e);
								throw new IOException("Could not connect to: " + connectAddress, e);
							}
						}
	
					} catch (IOException ee) {
						disconnect(channel, null, null, ee);
						throw new IOException("Connection failed", ee);
					}
				} catch (IOException eee) {
					disconnect(null, null, null, eee);
					future.completeExceptionally(new IOException("Connection failed", eee));
				}
			}
		});
		
		return future;
	}

	
	private void init(SocketChannel channel, SelectionKey inboundKey) throws IOException {
		final SelectionKey selectionKey = queue.register(channel);
		currentSelectionKey = selectionKey;

		selectionKey.attach(new SelectionKeyVisitor() {
			@Override
			public void visit(SelectionKey key) {
				if (closed) {
					disconnect(channel, inboundKey, null, null);
					return;
				}
				
				if (!channel.isOpen()) {
					return;
				}
				
				if (key.isReadable()) {
					
					while (!readings.isEmpty()) {
						Reading reading = readings.getLast();
						
						byte[] b;
						if (MutableByteArrays.isEmpty(reading.buffer)) {
							b = new byte[] {};
						} else {
							b = reading.buffer.bytes[reading.index];
						}
						int r;
						try {
							r = channel.read(ByteBuffer.wrap(b, reading.offset, b.length - reading.offset));
						} catch (IOException e) {
							LOGGER.trace("Read failed", e);
							disconnect(channel, inboundKey, selectionKey, e);
							return;
						}

						LOGGER.trace("Read: {} bytes", r);

						if (b.length == 0) {
							readings.removeLast();
							reading.future.complete(null);
						} else {
							if (r == 0) {
								break;
							}
							if (r < 0) {
								LOGGER.trace("Connection closed by peer");
								disconnect(channel, inboundKey, selectionKey, null);
								return;
							}
							
							reading.offset += r;
							if (reading.offset == b.length) {
								reading.index++;
								reading.offset = 0;
								if (reading.index == reading.buffer.bytes.length) {
									readings.removeLast();
									reading.future.complete(null);
								}
							}
						}
					}
					
					selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_READ);

				} else if (key.isWritable()) {
					
					while (!writings.isEmpty()) {
						Writing writing = writings.removeLast();
						
						if (writing.buffer == null) {
							LOGGER.trace("Gracefully closing");
							try {
								channel.close();
							} catch (IOException e) {
								writing.future.completeExceptionally(new IOException("Graceful close failed", e));
								disconnect(channel, inboundKey, selectionKey, e);
								return;
							}
							writing.future.complete(null);
						} else {
							for (byte[] b : writing.buffer.bytes) {
								try {
									channel.write(ByteBuffer.wrap(b));
								} catch (IOException e) {
									writing.future.completeExceptionally(new IOException("Write failed", e));
									disconnect(channel, inboundKey, selectionKey, e);
									return;
								}
							}
							writing.future.complete(null);
						}
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
	}
	
	private void disconnect(SocketChannel channel, SelectionKey inboundKey, SelectionKey selectionKey, IOException error) {
		LOGGER.trace("Socket disconnected", error);
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

		IOException e = (error == null) ? new IOException("Closed") : new IOException("Closed because of", error);
		for (Reading reading : readings) {
			if ((error == null) && MutableByteArrays.isEmpty(reading.buffer)) {
				reading.future.complete(null);
			} else {
				reading.future.completeExceptionally(e);
			}
		}
		for (Writing writing : writings) {
			writing.future.completeExceptionally(e);
		}
		readings.clear();
		writings.clear();

		currentChannel = null;
		currentInboundKey = null;
		currentSelectionKey = null;
		
		if (!closed) {
			closed = true;

			/*
			for (CompletableFuture<Void> onClose : onCloses) {
				onClose.complete(null);
			}
			*/

			queue.close();
		}
	}
	
	public CompletableFuture<Void> read(MutableByteArray buffer) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		queue.execute(new Runnable() {
			@Override
			public void run() {
				if (currentSelectionKey == null) {
					future.completeExceptionally(new IOException("Not open"));
					return;
				}
				readings.addLast(new Reading(future, buffer));
				currentSelectionKey.interestOps(currentSelectionKey.interestOps() | SelectionKey.OP_READ);
			}
		});
		
		return future;
	}
	
	public CompletableFuture<Void> write(ByteArray buffer) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		if ((buffer != null) && (SUPERVISION != null)) {
			SUPERVISION.set(ByteArrays.totalLength(buffer) / 1000d);
		}

		queue.execute(new Runnable() {
			@Override
			public void run() {
				if (currentSelectionKey == null) {
					future.completeExceptionally(new IOException("Not open"));
					return;
				}
				writings.addLast(new Writing(future, buffer));
				currentSelectionKey.interestOps(currentSelectionKey.interestOps() | SelectionKey.OP_WRITE);
			}
		});
		
		return future;
	}
	
	public void close() {
		queue.execute(new Runnable() {
			@Override
			public void run() {
				disconnect(currentChannel, currentInboundKey, currentSelectionKey, null);
			}
		});
	}
}
