package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SecureSocketManager implements Connector, Connecting, Closing, Failing, Receiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(SecureSocketManager.class);

	private final Trust trust;
	private final boolean clientMode;
	private final Executor executor;
	private final ByteBufferAllocator byteBufferAllocator;
	
	public Connector connector = null;
	public Connecting connecting = null;
	public Closing closing = null;
	public Failing failing = null;
	public Receiver receiver = null;
	
	private Deque<ByteBuffer> sent = new LinkedList<ByteBuffer>();
	private Deque<ByteBuffer> received = new LinkedList<ByteBuffer>();

	private SSLEngine engine = null;
	
	public Address connectAddress = null;
	
	public SecureSocketManager(Trust trust, boolean clientMode, Executor executor, ByteBufferAllocator byteBufferAllocator) {
		if (executor == null) {
			throw new NullPointerException("executor");
		}
		if (trust == null) {
			throw new NullPointerException("trust");
		}
		this.trust = trust;
		this.clientMode = clientMode;
		this.executor = executor;
		this.byteBufferAllocator = byteBufferAllocator;
	}
	
	private boolean continueSend(boolean force) {
		if (sent == null) {
			return false;
		}
		if (sent.isEmpty()) {
			if (!force) {
				return false;
			}
			sent.addFirst(ByteBuffer.allocate(0));
		}
		
		ByteBuffer b = sent.getFirst();
		ByteBuffer wrapBuffer = byteBufferAllocator.allocate();
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
			doClose(true);
			if (failing != null) {
				failing.failed(e);
			}
			return false;
		}
		
		wrapBuffer.flip();
		if (wrapBuffer.hasRemaining()) {
			connector.send(null, wrapBuffer);
		}
		return true;
	}
	
	private boolean continueReceive(boolean force) {
		if (received == null) {
			return false;
		}
		if (received.isEmpty()) {
			if (!force) {
				return false;
			}
			received.addFirst(ByteBuffer.allocate(0));
		}

		boolean underflow = false;
		
		ByteBuffer b = received.getFirst();
		ByteBuffer unwrapBuffer = byteBufferAllocator.allocate();
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
			doClose(true);
			if (failing != null) {
				failing.failed(e);
			}
			return false;
		}
		
		unwrapBuffer.flip();
		if (unwrapBuffer.hasRemaining()) {
			if (receiver != null) {
				receiver.received(null, unwrapBuffer);
			}
		}
		return !underflow;
	}
	
	private void doContinue() {
		if (connector == null) {
			return;
		}
		if (sent == null) {
			return;
		}
		if (received == null) {
			return;
		}

		if (engine == null) {
			engine = trust.createEngine(clientMode);
			try {
				engine.beginHandshake();
			} catch (IOException e) {
				LOGGER.error("Could not begin handshake", e);
				doClose(true);
				failing.failed(e);
			}
			if (connecting != null) {
				connecting.connected();
			}
		}
		
		if (engine == null) {
			return;
		}
		
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
				if (!continueSend(!clientMode)) {
					return;
				}
				break;
			case NEED_UNWRAP:
				if (!continueReceive(clientMode)) {
					return;
				}
				break;
			case FINISHED:
				break;
			case NOT_HANDSHAKING:
				if (!continueSend(false) && !continueReceive(false)) {
					return;
				}
				break;
			}
		}
	}
	
	private void doClose(boolean closeConnector) {
		sent = null;
		received = null;
		
		if (engine != null) {
			try {
				engine.closeInbound();
			} catch (IOException e) {
			}
			engine.closeOutbound();
		}

		if (closeConnector && (connector != null)) {
			connector.close();
		}
	}

	//
	
	@Override
	public void received(Address address, final ByteBuffer buffer) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (received == null) {
					return;
				}
				received.addLast(buffer);
				doContinue();
			}
		});
	}
	@Override
	public void closed() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				doClose(false);
				
				if (sent == null) {
					return;
				}
				if (received == null) {
					return;
				}
				if (closing != null) {
					closing.closed();
				}
			}
		});
	}
	@Override
	public void failed(final IOException e) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				doClose(false);
				
				if (sent == null) {
					return;
				}
				if (received == null) {
					return;
				}
				if (failing != null) {
					failing.failed(e);
				}
			}
		});
	}
	@Override
	public void connected() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				doContinue();
			}
		});
	}

	//
	
	@Override
	public void close() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				doClose(true);
			}
		});
	}
	@Override
	public Connector send(Address address, final ByteBuffer buffer) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (sent == null) {
					return;
				}
				sent.addLast(buffer);
				doContinue();
				return;
			}
		});
		return this;
	}
}
