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
import com.davfx.ninio.core.v3.TcpSocket.Builder;

public final class SslSocketBuilder implements TcpSocket.Builder {
	private static final Logger LOGGER = LoggerFactory.getLogger(SslSocketBuilder.class);

	private Trust trust = new Trust();
	
	private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();
	
	private Address connectAddress = null;
	
	private Connecting connecting = null;
	private Closing closing = null;
	private Failing failing = null;
	private Receiver receiver = null;
	
	private final TcpSocket.Builder wrappee;

	public SslSocketBuilder(TcpSocket.Builder wrappee) {
		this.wrappee = wrappee;
	}
	
	public Builder trust(Trust trust) {
		this.trust = trust;
		return this;
	}
	
	@Override
	public Builder closing(Closing closing) {
		this.closing = closing;
		return this;
	}

	@Override
	public Builder connecting(Connecting connecting) {
		this.connecting = connecting;
		return this;
	}
	
	@Override
	public Builder failing(Failing failing) {
		this.failing = failing;
		return this;
	}
	
	@Override
	public Builder receiving(Receiver receiver) {
		this.receiver = receiver;
		return this;
	}
	
	@Override
	public Builder with(ByteBufferAllocator byteBufferAllocator) {
		this.byteBufferAllocator = byteBufferAllocator;
		return this;
	}

	@Override
	public Builder to(Address connectAddress) {
		this.connectAddress = connectAddress;
		return this;
	}
	
	private static interface Send {
		boolean send(Connector connector);
	}
	private static interface Receive {
		boolean receive(Connector connector, boolean force);
	}
	private static interface Continue {
		void run(Connector connector);
	}
	private static interface Close {
		void close(Connector connector);
	}
		
	@Override
	public Connector create(Queue queue) {
		final Connecting thisConnecting = connecting;
		final Closing thisClosing = closing;
		final Failing thisFailing = failing;
		final Receiver thisReceiver = receiver;
		final ByteBufferAllocator thisByteBufferAllocator = byteBufferAllocator;
		final Address thisConnectAddress = connectAddress;

		final Deque<ByteBuffer> sent = new LinkedList<ByteBuffer>();
		final Deque<ByteBuffer> received = new LinkedList<ByteBuffer>();

		final SSLEngine engine = trust.createEngine(true);

		final Close doClose = new Close() {
			@Override
			public void close(Connector connector) {
				sent.clear();
				received.clear();
				
				try {
					engine.closeInbound();
				} catch (IOException e) {
				}
				engine.closeOutbound();

				if (connector != null) {
					connector.close();
				}
			}
		};
		
		final Send doSend = new Send() {
			@Override
			public boolean send(Connector connector) {
				if (sent.isEmpty()) {
					return false;
				}
				
				ByteBuffer b = sent.getFirst();
				ByteBuffer wrapBuffer = thisByteBufferAllocator.allocate();
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
					thisFailing.failed(e);
					doClose.close(connector);
					return false;
				}
				
				wrapBuffer.flip();
				if (wrapBuffer.hasRemaining()) {
					connector.send(null, wrapBuffer);
				}
				return true;
			}
		};
		
		final Receive doReceive = new Receive() {
			@Override
			public boolean receive(Connector connector, boolean force) {
				if (received.isEmpty()) {
					if (!force) {
						return false;
					}
					received.addFirst(ByteBuffer.allocate(0));
				}

				boolean underflow = false;
				
				ByteBuffer b = received.getFirst();
				ByteBuffer unwrapBuffer = thisByteBufferAllocator.allocate();
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
					thisFailing.failed(e);
					doClose.close(connector);
					return false;
				}
				
				unwrapBuffer.flip();
				if (unwrapBuffer.hasRemaining()) {
					thisReceiver.received(connector, null, unwrapBuffer);
				}
				return !underflow;
			}
		};
		
		final Continue doContinue = new Continue() {
			@Override
			public void run(Connector connector) {
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
						if (!doSend.send(connector)) {
							return;
						}
						break;
					case NEED_UNWRAP:
						if (!doReceive.receive(connector, true)) {
							return;
						}
						break;
					case FINISHED:
						break;
					case NOT_HANDSHAKING:
						if (!doSend.send(connector) && !doReceive.receive(connector, false)) {
							return;
						}
						break;
					}
				}
			}
		};

		final Connector connector = wrappee
				.with(thisByteBufferAllocator)
				.connecting(thisConnecting)
				.receiving(new Receiver() {
					@Override
					public void received(Connector connector, Address address, ByteBuffer buffer) {
						if (engine == null) {
							return;
						}
						received.addLast(buffer);
						doContinue.run(connector);
					}
				})
				.closing(new Closing() {
					@Override
					public void closed() {
						doClose.close(null);
						thisClosing.closed();
					}
				})
				.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						doClose.close(null);
						thisFailing.failed(e);
					}
				})
				.to(thisConnectAddress)
				.create(queue);

		queue.execute(new Runnable() {
			@Override
			public void run() {
				try {
					engine.beginHandshake();
				} catch (IOException e) {
					LOGGER.error("Could not begin handshake", e);
					doClose.close(connector);
					thisFailing.failed(e);
				}
			}
		});

		return new Connector() {
			@Override
			public void close() {
				doClose.close(connector);
			}
			
			@Override
			public Connector send(Address address, ByteBuffer buffer) {
				if (engine == null) {
					return this;
				}
				sent.addLast(buffer);
				doContinue.run(connector);
				return this;
			}
		};
	}
}
