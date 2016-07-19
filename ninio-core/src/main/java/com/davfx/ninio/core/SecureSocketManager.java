package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SecureSocketManager implements Connected, Connection {
	private static final Logger LOGGER = LoggerFactory.getLogger(SecureSocketManager.class);

	public static final int REQUIRED_BUFFER_SIZE = 17 * 1024;

	private final Trust trust;
	private final boolean clientMode;
	private final Executor executor;
	private final ByteBufferAllocator byteBufferAllocator;
	
	private Connected connecting = null;
	private Connection callback = null;
	private Address connectAddress;
	
	private static final class ToWrite {
		public final ByteBuffer buffer;
		public final SendCallback callback;
		public ToWrite(ByteBuffer buffer, SendCallback callback) {
			this.buffer = buffer;
			this.callback = callback;
		}
	}
	
	private Deque<ToWrite> sent = new LinkedList<>();
	private Deque<ByteBuffer> received = new LinkedList<>();

	private SSLEngine engine = null;
	
	private boolean closed = false;
	
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
	
	private void fail(IOException ioe) {
		LOGGER.error("SSL error", ioe);
		doClose();
		
		if (!closed) {
			closed = true;
			callback.failed(ioe);
		}
	}
	
	private boolean continueSend(boolean force) {
		if (sent == null) {
			return false;
		}
		if (sent.isEmpty()) {
			if (!force) {
				return false;
			}
			sent.addFirst(new ToWrite(ByteBuffer.allocate(0), new Nop()));
		}
		
		ToWrite toWrite = sent.getFirst();
		ByteBuffer wrapBuffer = byteBufferAllocator.allocate();
		SendCallback sendCallback = null;
		try {
			SSLEngineResult r = engine.wrap(toWrite.buffer, wrapBuffer);
			if (!toWrite.buffer.hasRemaining()) {
				sendCallback = toWrite.callback;
			}

			if (r.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
				throw new IOException("Buffer overflow, allocator should allocate bigger buffers");
			}
			
			if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
				throw new IOException("Buffer underflow should not happen");
			}
		} catch (IOException e) {
			fail(e);
			return false;
		}

		if (sendCallback == null) {
			sendCallback = new Nop();
		} else {
			sent.removeFirst();
		}
			
		wrapBuffer.flip();
		if (wrapBuffer.hasRemaining()) {
			if (!closed) {
				connecting.send(null, wrapBuffer, sendCallback);
			}
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
			fail(e);
			return false;
		}
		
		unwrapBuffer.flip();
		if (unwrapBuffer.hasRemaining()) {
			if (!closed) {
				callback.received(null, unwrapBuffer);
			}
		}
		return !underflow;
	}
	
	private void doContinue() {
		if (closed || (connecting == null) || (callback == null) || (connectAddress == null)) {
			// LOGGER.trace("Not prepared (clientMode = {})", clientMode);
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
				fail(e);
				return;
			}
			callback.connected(connectAddress);
		}
		
		if (engine == null) {
			return;
		}
		
		while (true) {
			// LOGGER.trace("Current handshake status: {} (clientMode = {})", engine.getHandshakeStatus(), clientMode);
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
	
	private void doClose() {
		if (sent != null) {
			IOException e = new IOException("SSL engine failed");
			for (ToWrite toWrite : sent) {
				toWrite.callback.failed(e);
			}
		}

		sent = null;
		received = null;
		
		if (engine != null) {
			try {
				engine.closeInbound();
			} catch (IOException e) {
			}
			engine.closeOutbound();
			engine = null;
		}

		if (connecting != null) {
			connecting.close();
			connecting = null;
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
				doClose();

				if (!closed) {
					closed = true;
					callback.closed();
				}
			}
		});
	}
	
	@Override
	public void failed(final IOException e) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				fail(e);
			}
		});
	}
	
	@Override
	public void connected(final Address address) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (address != null) {
					connectAddress = address;
				}
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
				doClose();
			}
		});
	}
	
	@Override
	public void send(Address address, final ByteBuffer buffer, final SendCallback callback) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (sent == null) {
					return;
				}
				sent.addLast(new ToWrite(buffer, callback));
				doContinue();
			}
		});
	}
	
	//
	
	public void prepare(final Address address, final Connected connecting) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (address != null) {
					connectAddress = address;
				}
				SecureSocketManager.this.connecting = connecting;
				doContinue();
			}
		});
	}
	public void prepare(final Connection callback) {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				SecureSocketManager.this.callback = callback;
				doContinue();
			}
		});
	}
}
