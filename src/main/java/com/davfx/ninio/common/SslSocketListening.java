package com.davfx.ninio.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO retravailler
public final class SslSocketListening implements SocketListening {
	private static final Logger LOGGER = LoggerFactory.getLogger(SslSocketListening.class);

	private final SocketListening wrappee;
	private final Trust trust;

	public SslSocketListening(Trust trust, SocketListening wrappee) {
		this.wrappee = wrappee;
		this.trust = trust;
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
		Inner inner = new Inner(trust, address, connection);
		CloseableByteBufferHandler write = wrappee.connected(address, inner);
		return inner.handler(write);
	}
	
	private static final class Inner implements CloseableByteBufferHandler {
		private final SSLEngine engine;
		private final int applicationBufferSize;
		private final int packetBufferSize;
		private final Address address;
		private final CloseableByteBufferHandler connection;
		private final Deque<ByteBuffer> toSend = new LinkedList<ByteBuffer>();
		private final Deque<ByteBuffer> toReceive = new LinkedList<ByteBuffer>();
		private ByteBuffer underflow = null;
		private CloseableByteBufferHandler write;

		public Inner(Trust trust, Address address, CloseableByteBufferHandler connection) {
			engine = trust.createEngine(false);
			SSLSession session = engine.getSession();
			applicationBufferSize = session.getApplicationBufferSize();
			packetBufferSize = session.getPacketBufferSize();
			this.address = address;
			this.connection = connection;
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
			closeEngine();
			connection.close();
		}
		
		private void closeAll() {
			closeEngine();
			if (write != null) {
				write.close();
				write = null;
			}
		}
		
		private void doContinue(SSLEngineResult r) {
			while (true) {
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
					doSend();
					return;
				case NEED_UNWRAP:
					doReceive();
					return;
				case FINISHED:
					doSend();
					doReceive();
					return;
				case NOT_HANDSHAKING:
					// doSend();
					doReceive();
					return;
				}
			}
		}

		private void doSend() {
			ByteBuffer b;
			if (toSend.isEmpty()) { // This is a difference with the SSL client
				b = ByteBuffer.allocate(0);
			} else {
				b = toSend.getFirst();
			}
			ByteBuffer wrapBuffer = ByteBuffer.allocate(packetBufferSize);
			SSLEngineResult r;
			try {
				r = engine.wrap(b, wrapBuffer);
				if (!b.hasRemaining() && !toSend.isEmpty()) {
					toSend.removeFirst();
				}
				
				switch (r.getStatus()) {
				case BUFFER_UNDERFLOW:
				case BUFFER_OVERFLOW:
					throw new IOException("Should not happen: " + r.getStatus());
				case CLOSED:
				case OK:
					break;
				}
			} catch (SSLException e) {
				LOGGER.error("SSL error", e);
				closeAll();
				return;
			} catch (IOException e) {
				LOGGER.error("SSL error", e);
				closeAll();
				return;
			}
			
			wrapBuffer.flip();
			if (wrapBuffer.hasRemaining()) {
				connection.handle(address, wrapBuffer);
			}
			doContinue(r);
		}
		private void doReceive() {
			if (write == null) {
				return;
			}
			if (toReceive.isEmpty()) {
				return;
			}
			ByteBuffer b = toReceive.getFirst();
			if (underflow != null) {
				LOGGER.debug("SSL server underflows, it may be slow");
				int ur = underflow.remaining();
				byte[] bb = new byte[ur + b.remaining()];
				underflow.get(bb, 0, ur);
				b.get(bb, ur, b.remaining());
				b = ByteBuffer.wrap(bb);
				underflow = null;
				toReceive.removeFirst();
				toReceive.addFirst(b);
			}
			ByteBuffer unwrapBuffer = ByteBuffer.allocate(applicationBufferSize);
			SSLEngineResult r;
			try {
				r = engine.unwrap(b, unwrapBuffer);
				if (!b.hasRemaining()) {
					toReceive.removeFirst();
				}
				
				switch (r.getStatus()) {
				case BUFFER_UNDERFLOW:
					if (underflow == null) {
						underflow = b;
						toReceive.removeFirst();
						doReceive();
					}
					break;
				case BUFFER_OVERFLOW:
					throw new IOException("Should not happen: " + r.getStatus());
				case CLOSED:
				case OK:
					break;
				}
			} catch (SSLException e) {
				LOGGER.error("SSL error", e);
				closeAll();
				return;
			} catch (IOException e) {
				LOGGER.error("SSL error", e);
				closeAll();
				return;
			}
			
			unwrapBuffer.flip();
			if (unwrapBuffer.hasRemaining()) {
				write.handle(address, unwrapBuffer);
			}
			doContinue(r);
		}
		
		@Override
		public void handle(Address address, ByteBuffer buffer) {
			toSend.addLast(buffer);
			doSend();
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
					toReceive.addLast(buffer);
					doReceive();
				}
			};
		}
	}
}
