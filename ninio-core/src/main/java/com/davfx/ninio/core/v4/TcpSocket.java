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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ClassThreadFactory;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.DateUtils;
import com.typesafe.config.Config;

public final class TcpSocket {

	private static final Logger LOGGER = LoggerFactory.getLogger(TcpSocket.class);
	
	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(com.davfx.ninio.core.TcpSocket.class.getPackage().getName());
	private static final double SOCKET_TIMEOUT = ConfigUtils.getDuration(CONFIG, "tcp.socket.timeout");
	private static final long SOCKET_WRITE_BUFFER_SIZE = CONFIG.getBytes("tcp.socket.write").longValue();
	private static final long SOCKET_READ_BUFFER_SIZE = CONFIG.getBytes("tcp.socket.read").longValue();

	private static final double SUPERVISION_DISPLAY = ConfigUtils.getDuration(CONFIG, "supervision.tcp.display");

	private static final class Supervision {
		private static double floorTime(double now, double period) {
	    	double precision = 1000d;
	    	long t = (long) (now * precision);
	    	long d = (long) (period * precision);
	    	return (t - (t % d)) / precision;
		}
		
		private final AtomicLong max = new AtomicLong(0L);
		
		public Supervision() {
			double now = DateUtils.now();
			double startDisplay = SUPERVISION_DISPLAY - (now - floorTime(now, SUPERVISION_DISPLAY));
			
			ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(TcpSocket.Supervision.class, true));

			executor.scheduleAtFixedRate(new Runnable() {
				@Override
				public void run() {
					long m = max.getAndSet(0L);
					LOGGER.trace("[TCP Supervision] max = {} Kb", m / 1000d);
				}
			}, (long) (startDisplay * 1000d), (long) (SUPERVISION_DISPLAY * 1000d), TimeUnit.MILLISECONDS);
		}
		
		public void setWriteMax(long newMax) {
			while (true) {
				long curMax = max.get();
				if (curMax >= newMax) {
					break;
				}
				if (max.compareAndSet(curMax, newMax)) {
					break;
				}
			}
		}
	}
	
	private static final Supervision SUPERVISION = (SUPERVISION_DISPLAY > 0d) ? new Supervision() : null;

	private Queue queue;
	
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
	private final Deque<CompletableFuture<Void>> onCloses = new LinkedList<>();
	
	public TcpSocket(Ninio ninio) {
		queue = ninio.register(NinioPriority.REGULAR);
	}
	
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
	
	public TcpSocket bind(Address bindAddress) {
		this.bindAddress = bindAddress;
		return this;
	}
	
	public CompletableFuture<Void> connect(Address connectAddress) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		queue.execute(new Runnable() {
			@Override
			public void run() {
				if (currentChannel != null) {
					future.completeExceptionally(new IOException("Already connected"));
					return;
				}
				
				try {
					final SocketChannel channel = SocketChannel.open();
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
						final SelectionKey inboundKey = queue.register(channel);
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
													
													byte[] b = reading.buffer.bytes[reading.index];
													int r;
													try {
														r = channel.read(ByteBuffer.wrap(b, reading.offset, b.length - reading.offset));
													} catch (IOException e) {
														LOGGER.trace("Read failed", e);
														disconnect(channel, inboundKey, selectionKey, e);
														return;
													}
													
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
												
												selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_READ);
	
											} else if (key.isWritable()) {
												
												while (!writings.isEmpty()) {
													Writing writing = writings.removeLast();
													
													if (writing.buffer == null) {
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
								} catch (IOException e) {
									disconnect(channel, inboundKey, null, e);
									future.completeExceptionally(new IOException("Connection failed", e));
									return;
								}
	
								future.complete(null);
							}
						});
	
						if (bindAddress != null) {
							try {
								channel.socket().bind(new InetSocketAddress(InetAddress.getByAddress(bindAddress.ip), bindAddress.port));
							} catch (IOException e) {
								disconnect(channel, inboundKey, null, e);
								future.completeExceptionally(new IOException("Could not bind to: " + bindAddress, e));
								return;
							}
						}
	
						try {
							InetSocketAddress a = new InetSocketAddress(InetAddress.getByAddress(connectAddress.ip), connectAddress.port);
							LOGGER.trace("Connecting to: {}", a);
							channel.connect(a);
						} catch (IOException e) {
							LOGGER.error("Could not connect to: " + connectAddress, e);
							disconnect(channel, inboundKey, null, e);
							future.completeExceptionally(new IOException("Could not connect to: " + connectAddress, e));
							return;
						}
	
					} catch (IOException ee) {
						LOGGER.error("Connection failed", ee);
						disconnect(channel, null, null, ee);
						future.completeExceptionally(new IOException("Connection failed", ee));
						return;
					}
				} catch (IOException eee) {
					LOGGER.error("Connection failed", eee);
					disconnect(null, null, null, eee);
					future.completeExceptionally(new IOException("Connection failed", eee));
					return;
				}
			}
		});
		
		return future;
	}

	private void disconnect(SocketChannel channel, SelectionKey inboundKey, SelectionKey selectionKey, IOException error) {
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
			reading.future.completeExceptionally(e);
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

			for (CompletableFuture<Void> onClose : onCloses) {
				onClose.complete(null);
			}

			queue.close();
		}
	}
	
	public CompletableFuture<Void> read(MutableByteArray buffer) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		queue.execute(new Runnable() {
			@Override
			public void run() {
				if (currentSelectionKey == null) {
					future.completeExceptionally(new IOException("Closed"));
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

		if (SUPERVISION != null) {
			SUPERVISION.setWriteMax(ByteArrays.totalLength(buffer));
		}

		queue.execute(new Runnable() {
			@Override
			public void run() {
				if (currentSelectionKey == null) {
					future.completeExceptionally(new IOException("Closed"));
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
