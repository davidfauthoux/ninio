package com.davfx.ninio.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SslSocketListening implements SocketListening {
	private static final Logger LOGGER = LoggerFactory.getLogger(SslSocketListening.class);

	private final ByteBufferAllocator allocator;
	private final SocketListening wrappee;
	private final Trust trust;

	public SslSocketListening(Trust trust, ByteBufferAllocator allocator, SocketListening wrappee) {
		this.wrappee = wrappee;
		this.trust = trust;
		this.allocator = allocator;
	}
	
	@Override
	public void close() {
		wrappee.close();
	}
	
	@Override
	public void failed(IOException e) {
		wrappee.failed(e);
	}
	
	@Override
	public CloseableByteBufferHandler connected(Address address, CloseableByteBufferHandler connection) {
		Inner inner = new Inner(trust, address, allocator, connection);
		CloseableByteBufferHandler write = wrappee.connected(address, inner);
		return inner.handler(write);
	}
	
	private static final class Inner implements CloseableByteBufferHandler {
		private final ByteBufferAllocator allocator;
		private final SSLEngine engine;
		private final Address address;
		private final CloseableByteBufferHandler connection;
		private final Deque<ByteBuffer> sent = new LinkedList<ByteBuffer>();
		private final Deque<ByteBuffer> received = new LinkedList<ByteBuffer>();
		private CloseableByteBufferHandler write;

		public Inner(Trust trust, Address address, ByteBufferAllocator allocator, CloseableByteBufferHandler connection) {
			engine = trust.createEngine(false);
			this.allocator = allocator;
			this.address = address;
			this.connection = connection;

			try {
				engine.beginHandshake();
			} catch (IOException e) {
				closeEngine();
				LOGGER.error("Could not begin handshake", e);
			}
		}
		
		private void clear() {
			sent.clear();
			received.clear();
		}
		
		private void closeEngine() {
			try {
				engine.closeInbound();
			} catch (SSLException e) {
			}
			engine.closeOutbound();
		}
		
		@Override
		public void close() {
			clear();
			closeEngine();
			connection.close();
		}
		
		private void closeAll() {
			clear();
			closeEngine();
			if (write != null) {
				write.close();
				write = null;
			}
		}
		
		private boolean receive() {
			if (received.isEmpty()) {
				return false;
			}
			
			boolean underflow = false;
			
			ByteBuffer b = received.getFirst();
			ByteBuffer unwrapBuffer = allocator.allocate();
			try {
				SSLEngineResult r = engine.unwrap(b, unwrapBuffer);
				if (!b.hasRemaining()) {
					received.removeFirst();
				}
				
				if (r.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
					throw new IOException("Buffer overflow, allocator should allocate bigger buffers");
				}

				if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
					if (received.size() <= 1) {
						underflow = true;
					} else {
						ByteBuffer b0 = received.removeFirst();
						ByteBuffer b1 = received.removeFirst();
						ByteBuffer b01 = ByteBuffer.allocate(b0.remaining() + b1.remaining());
						int l0 = b0.remaining();
						int l1 = b1.remaining();
						b0.get(b01.array(), 0, l0);
						b1.get(b01.array(), l0, l1);
						b01.limit(l0 + l1);
						received.addFirst(b01);
					}
				}
			} catch (IOException e) {
				LOGGER.error("SSL error", e);
				closeAll();
				return false;
			}
			
			unwrapBuffer.flip();
			if (unwrapBuffer.hasRemaining()) {
				write.handle(address, unwrapBuffer);
			}
			return !underflow;
		}
		
		private boolean send(boolean force) {
			if (sent.isEmpty()) {
				if (!force) {
					return false;
				}
				sent.addLast(ByteBuffer.allocate(0));
			}
			
			ByteBuffer b = sent.getFirst();
			ByteBuffer wrapBuffer = allocator.allocate();
			try {
				SSLEngineResult r = engine.wrap(b, wrapBuffer);
				if (!b.hasRemaining()) {
					sent.removeFirst();
				}

				if (r.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
					throw new IOException("Buffer overflow, allocator should allocate bigger buffers");
				}
				
				if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
					throw new IOException("Buffer underflow should not happen");
				}
			} catch (IOException e) {
				LOGGER.error("SSL error", e);
				closeAll();
				return false;
			}
			
			wrapBuffer.flip();
			if (wrapBuffer.hasRemaining()) {
				connection.handle(address, wrapBuffer);
			}
			return true;
		}
		
		private void doContinue() {
			while (true) {
				if (engine == null) {
					return;
				}
				
				switch (engine.getHandshakeStatus()) {
				case NEED_TASK:
				    while (true) {
				    	Runnable runnable = engine.getDelegatedTask();
				    	if (runnable == null) {
				    		break;
				    	}
				    	runnable.run();
				    }
				    break;
				case NEED_WRAP:
					if (!send(true)) {
						return;
					}
					break;
				case NEED_UNWRAP:
					if (!receive()) {
						return;
					}
					break;
				case FINISHED:
					break;
				case NOT_HANDSHAKING:
					if (!send(false) && !receive()) {
						return;
					}
					break;
				}
			}
		}
	
		@Override
		public void handle(Address address, ByteBuffer buffer) {
			sent.addLast(buffer);
			doContinue();
		}
		
		CloseableByteBufferHandler handler(final CloseableByteBufferHandler write) {
			this.write = write;
			return new CloseableByteBufferHandler() {
				@Override
				public void close() {
					closeEngine();
					write.close();
				}
				@Override
				public void handle(Address address, ByteBuffer buffer) {
					received.addLast(buffer);
					doContinue();
				}
			};
		}
	}
}
