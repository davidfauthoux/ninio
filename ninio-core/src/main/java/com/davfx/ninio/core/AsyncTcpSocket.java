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
	private Connecter.Connecting connector = null;
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
	
	private static final class VoidFuture implements Future<Void> {
		public VoidFuture() {
		}
		@Override
		public Void get() throws IOException {
			return null;
		}
	}
	
	private static final class ByteBufferFuture implements Future<ByteBuffer> {
		private final ByteBuffer buffer;
		public ByteBufferFuture(ByteBuffer buffer) {
			this.buffer = buffer;
		}
		@Override
		public ByteBuffer get() throws IOException {
			return buffer;
		}
	}
	
	private static final class ErrorFuture implements Future<Void> {
		private final IOException error;
		
		public ErrorFuture(IOException error) {
			this.error = error;
		}

		@Override
		public Void get() throws IOException {
			throw error;
		}
	}

	private final class WriteFuture implements Future<Void>, Connecter.Connecting.Callback {
		private boolean sent = false;
		private IOException ioe = null;

		public WriteFuture() {
		}
		
		@Override
		public void sent() {
			synchronized (lock) {
				sent = true;
				lock.notifyAll();
			}
		}
		
		@Override
		public void failed(IOException ioe) {
			synchronized (lock) {
				this.ioe = ioe;
				lock.notifyAll();
			}
		}
		
		@Override
		public Void get() throws IOException {
			synchronized (lock) {
				while (true) {
					if (error != null) {
						throw error;
					}
					if (ioe != null) {
						throw ioe;
					}
					if (sent) {
						return null;
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

	private final class CloseFuture implements Future<Void> {
		public CloseFuture() {
		}
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
	}

	private final class ConnectedFuture implements Future<Void> {
		public ConnectedFuture() {
		}
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
	}

	private final Deque<InternalFuture> readFutures = new LinkedList<>();

	public AsyncTcpSocket(Ninio ninio) {
		this.ninio = ninio;
	}

	public void bind(Address address) {
		builder.bind(address);
	}
	
	public Future<Void> connect(Address address) {
		builder.to(address);
		
		Connecter.Connecting c = ninio.create(builder).connect(new Connecter.Callback() {
			
			@Override
			public void received(Address address, ByteBuffer buffer) {
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
			
			@Override
			public void failed(IOException ioe) {
				synchronized (lock) {
					if (error == null) {
						error = ioe;
						lock.notifyAll();
					}
				}
			}
			
			@Override
			public void connected(Address address) {
				synchronized (lock) {
					connected = true;
					lock.notifyAll();
				}
			}
			
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
		
		synchronized (lock) {
			connector = c;
		}
		
		return new ConnectedFuture();
	}
	
	public Future<ByteBuffer> read() {
		synchronized (lock) {
			if (readPackets.isEmpty()) {
				LOGGER.debug("Adding future");
				InternalFuture f = new InternalFuture();
				readFutures.addLast(f);
				return f;
			} else {
				return new ByteBufferFuture(readPackets.removeFirst());
			}
		}
	}
	
	public Future<Void> write(ByteBuffer packet) {
		Connecter.Connecting c;
		synchronized (lock) {
			c = connector;
			if (error != null) {
				return new ErrorFuture(error);
			}
		}
		if (c == null) {
			return new VoidFuture();
		}
		WriteFuture f = new WriteFuture();
		c.send(null, packet.duplicate(), f);
		return f;
	}
	
	public Future<Void> close() {
		Connecter.Connecting c;
		synchronized (lock) {
			c = connector;
			if (error != null) {
				error = new IOException("Closed");
			}
		}
		if (c == null) {
			return new VoidFuture();
		}
		c.close();
		return new CloseFuture();
	}
}
