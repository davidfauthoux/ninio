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

public final class SslReady implements Ready {
	private static final Logger LOGGER = LoggerFactory.getLogger(SslReady.class);

	private final Ready wrappee;
	private final SSLEngine engine;
	private final int applicationBufferSize;
	private final int packetBufferSize;

	public SslReady(Trust trust, Ready wrappee) {
		this.wrappee = wrappee;
		engine = trust.createEngine(true);

		SSLSession session = engine.getSession();
		packetBufferSize = session.getPacketBufferSize();
		applicationBufferSize = session.getApplicationBufferSize();
	}
	
	private void closeEngine() {
		try {
			engine.closeInbound();
		} catch (SSLException e) {
		}
		engine.closeOutbound();
	}
	
	@Override
	public void connect(final Address address, final ReadyConnection connection) {
		wrappee.connect(address, new ReadyConnection() {
			private CloseableByteBufferHandler write;
			private final Deque<ByteBuffer> toSend = new LinkedList<ByteBuffer>();
			private final Deque<ByteBuffer> toReceive = new LinkedList<ByteBuffer>();
			private ByteBuffer underflow = null;
			
			@Override
			public void failed(IOException e) {
				closeEngine();
				connection.failed(e);
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
						doSend(); // This is a difference with the SSL server
						doReceive();
						return;
					}
				}
			}

			private void doSend() {
				if (write == null) {
					return;
				}
				if (toSend.isEmpty()) {
					return;
				}

				ByteBuffer b = toSend.getFirst();
				ByteBuffer wrapBuffer = ByteBuffer.allocate(packetBufferSize);
				SSLEngineResult r;
				try {
					r = engine.wrap(b, wrapBuffer);
					if (!b.hasRemaining()) {
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
					write.handle(address, wrapBuffer);
				}
				doContinue(r);
			}
			private void doReceive() {
				if (toReceive.isEmpty()) {
					return;
				}
				ByteBuffer b = toReceive.getFirst();
				if (underflow != null) {
					LOGGER.debug("SSL client underflows, it may be slow");
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
					connection.handle(address, unwrapBuffer);
				}
				doContinue(r);
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				toReceive.addLast(buffer);
				doReceive();
			}
			
			@Override
			public void connected(FailableCloseableByteBufferHandler write) {
				this.write = write;
				connection.connected(new FailableCloseableByteBufferHandler() {
					@Override
					public void close() {
						closeEngine();
						write.close();
					}
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						toSend.addLast(buffer);
						doSend();
					}
					@Override
					public void failed(IOException e) {
						close();
					}
				});
			}
		});
	}
}
