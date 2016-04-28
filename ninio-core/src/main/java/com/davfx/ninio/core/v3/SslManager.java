package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;

final class SslManager implements Connector, Connecting, Closing, Failing, Receiver {
	private static final Logger LOGGER = LoggerFactory.getLogger(SslManager.class);

	private final ByteBufferAllocator byteBufferAllocator;
	private final Trust trust;
	
	public Connector connector = null;
	public Connecting connecting = null;
	public Closing closing = null;
	public Failing failing = null;
	public Receiver receiver = null;
	
	private Deque<ByteBuffer> sent = new LinkedList<ByteBuffer>();
	private Deque<ByteBuffer> received = new LinkedList<ByteBuffer>();

	private SSLEngine engine = null;

	public SslManager(Trust trust, ByteBufferAllocator byteBufferAllocator) {
		this.trust = trust;
		this.byteBufferAllocator = byteBufferAllocator;
	}
	
	private boolean continueSend() {
		if (sent == null) {
			return false;
		}
		if (sent.isEmpty()) {
			return false;
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
			failing.failed(e);
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
			failing.failed(e);
			return false;
		}
		
		unwrapBuffer.flip();
		if (unwrapBuffer.hasRemaining()) {
			receiver.received(this, null, unwrapBuffer);
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
			engine = trust.createEngine(true);
			try {
				engine.beginHandshake();
			} catch (IOException e) {
				LOGGER.error("Could not begin handshake", e);
				doClose(true);
				failing.failed(e);
			}
			connecting.connected(this);
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
				if (!continueSend()) {
					return;
				}
				break;
			case NEED_UNWRAP:
				if (!continueReceive(true)) {
					return;
				}
				break;
			case FINISHED:
				break;
			case NOT_HANDSHAKING:
				if (!continueSend() && !continueReceive(false)) {
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
	public void received(Connector connector, Address address, ByteBuffer buffer) {
		if (received == null) {
			return;
		}
		received.addLast(buffer);
		doContinue();
	}
	@Override
	public void closed() {
		doClose(false);
		
		if (sent == null) {
			return;
		}
		if (received == null) {
			return;
		}
		closing.closed();
	}
	@Override
	public void failed(IOException e) {
		doClose(false);
		
		if (sent == null) {
			return;
		}
		if (received == null) {
			return;
		}
		failing.failed(e);
	}
	@Override
	public void connected(Connector connector) {
		doContinue();
	}

	//
	
	@Override
	public void close() {
		doClose(true);
	}
	@Override
	public Connector send(Address address, ByteBuffer buffer) {
		if (sent == null) {
			return this;
		}
		sent.addLast(buffer);
		doContinue();
		return this;
	}
}
