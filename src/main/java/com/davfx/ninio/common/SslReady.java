package com.davfx.ninio.common;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SslReady implements Ready {
	private static final Logger LOGGER = LoggerFactory.getLogger(SslReady.class);

	private final Ready wrappee;
	private SSLEngine engine;
	private final ByteBufferAllocator allocator;
	//%% private final int applicationBufferSize;
	//%% private final int packetBufferSize;

	public SslReady(Trust trust, ByteBufferAllocator allocator, Ready wrappee) {
		this.wrappee = wrappee;
		this.allocator = allocator;
		engine = trust.createEngine(true);

		//%% SSLSession session = engine.getSession();
		//%% packetBufferSize = session.getPacketBufferSize();
		//%% applicationBufferSize = session.getApplicationBufferSize();
		
		try {
			engine.beginHandshake();
		} catch (IOException e) {
			closeEngine();
			LOGGER.error("Could not begin handshake", e);
		}
	}
	
	private void closeEngine() {
		if (engine == null) {
			return;
		}
		try {
			engine.closeInbound();
		} catch (IOException e) {
		}
		engine.closeOutbound();
		engine = null;
	}
	
	@Override
	public void connect(final Address address, final ReadyConnection connection) {
		if (engine == null) {
			connection.failed(new IOException("Invalid engine"));
			return;
		}
		
		wrappee.connect(address, new ReadyConnection() {
			private CloseableByteBufferHandler write;
			private final Deque<ByteBuffer> sent = new LinkedList<ByteBuffer>();
			private final Deque<ByteBuffer> received = new LinkedList<ByteBuffer>();
			
			private void clear() {
				sent.clear();
				received.clear();
			}
			
			@Override
			public void failed(IOException e) {
				clear();
				closeEngine();
				connection.failed(e);
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
					connection.handle(address, unwrapBuffer);
				}
				return !underflow;
			}
			
			private boolean send() {
				if (sent.isEmpty()) {
					return false;
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
					write.handle(address, wrapBuffer);
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
						if (!send()) {
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
						if (!send() && !receive()) {
							return;
						}
						break;
					}
				}
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				if (engine == null) {
					return;
				}
				
				received.add(buffer);
				doContinue();
			}
			
			@Override
			public void connected(final FailableCloseableByteBufferHandler write) {
				this.write = write;
				
				connection.connected(new FailableCloseableByteBufferHandler() {
					@Override
					public void close() {
						closeEngine();
						write.close();
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						if (engine == null) {
							return;
						}

						sent.add(buffer);
						doContinue();
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
