package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AsyncTcpSocket {
	private static final Logger LOGGER = LoggerFactory.getLogger(AsyncTcpSocket.class);

	private final Object lock = new Object();
	private final Ninio ninio;
	private final TcpSocket.Builder builder = TcpSocket.builder();
	private Connector connector = null;
	private boolean connected = false;
	private boolean closed = false;
	private IOException error = null;
	private final Deque<ByteBuffer> readPackets = new LinkedList<>();
	private final class InternalFuture implements Future<ByteBuffer> {
		private ByteBuffer packet = null;
		public InternalFuture() {
		}
		
		@Override
		public ByteBuffer get() throws IOException {
			synchronized (lock) {
				while (true) {
					if (error != null) {
						throw error;
					}
					if (packet != null) {
						return packet;
					}
					LOGGER.debug("Waiting in future");
					try {
						lock.wait();
					} catch (InterruptedException e) {
					}
				}
			}
		}
	}
	private final Deque<InternalFuture> readFutures = new LinkedList<>();

	public AsyncTcpSocket(Ninio ninio) {
		this.ninio = ninio;
		
		builder.connecting(new Connecting() {
			@Override
			public void connected(Connector connector, Address address) {
				synchronized (lock) {
					connected = true;
					lock.notifyAll();
				}
			}
		});
		builder.closing(new Closing() {
			@Override
			public void closed() {
				synchronized (lock) {
					closed = true;
					if (error == null) {
						error = new IOException("Closed by peer");
					}
					lock.notifyAll();
				}
			}
		});
		builder.failing(new Failing() {
			@Override
			public void failed(IOException e) {
				synchronized (lock) {
					if (error == null) {
						error = e;
						lock.notifyAll();
					}
				}
			}
		});
		builder.receiving(new Receiver() {
			@Override
			public void received(Connector connector, Address address, ByteBuffer buffer) {
				LOGGER.debug("Packet received ({} bytes)", buffer.remaining());
				synchronized (lock) {
					if (readFutures.isEmpty()) {
						LOGGER.debug("No future, keeping packet");
						readPackets.addLast(buffer);
					} else {
						LOGGER.debug("Pushing packet to future");
						readFutures.removeFirst().packet = buffer;
						lock.notifyAll();
					}
				}
			}
		});
	}

	public void bind(Address address) {
		builder.bind(address);
	}
	
	public Future<Void> connect(Address address) {
		builder.to(address);
		Connector c = ninio.create(builder);
		synchronized (lock) {
			connector = c;
		}
		return new Future<Void>() {
			@Override
			public Void get() throws IOException {
				synchronized (lock) {
					while (true) {
						if (error != null) {
							throw error;
						}
						if (connected) {
							return null;
						}
						try {
							lock.wait();
						} catch (InterruptedException e) {
						}
					}
				}
			}
		};
	}
	
	public Future<ByteBuffer> read() {
		synchronized (lock) {
			if (readPackets.isEmpty()) {
				LOGGER.debug("Adding future");
				InternalFuture f = new InternalFuture();
				readFutures.addLast(f);
				return f;
			} else {
				final ByteBuffer p = readPackets.removeFirst();
				return new Future<ByteBuffer>() {
					@Override
					public ByteBuffer get() throws IOException {
						return p;
					}
				};
			}
		}
	}
	
	public void write(ByteBuffer packet) {
		Connector c;
		synchronized (lock) {
			c = connector;
			if (error != null) {
				c = null;
			}
		}
		if (c == null) {
			return;
		}
		c.send(null, packet.duplicate());
	}
	
	public Future<Void> close() {
		Connector c;
		synchronized (lock) {
			c = connector;
			if (error != null) {
				error = new IOException("Closed");
			}
		}
		if (c == null) {
			return new Future<Void>() {
				@Override
				public Void get() {
					return null;
				}
			};
		}
		c.close();
		return new Future<Void>() {
			@Override
			public Void get() {
				synchronized (lock) {
					while (true) {
						if (closed) {
							return null;
						}
						try {
							lock.wait();
						} catch (InterruptedException e) {
						}
					}
				}
			}
		};
	}
}
