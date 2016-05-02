package com.davfx.ninio.core.v3;

import java.util.concurrent.Executor;

public final class SslSocketServerBuilder {
	private Trust trust = new Trust();
	private Executor executor = null;
	private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();

	private Listening listening = new Listening() {
		@Override
		public void connecting(Connector connector, SocketBuilder builder) {
			connector.close();
		}
	};

	public SslSocketServerBuilder() {
	}
	
	public SslSocketServerBuilder trust(Trust trust) {
		this.trust = trust;
		return this;
	}
	public SslSocketServerBuilder with(Executor executor) {
		this.executor = executor;
		return this;
	}
	public SslSocketServerBuilder with(ByteBufferAllocator byteBufferAllocator) {
		this.byteBufferAllocator = byteBufferAllocator;
		return this;
	}
	
	public SslSocketServerBuilder listening(Listening listening) {
		this.listening = listening;
		return this;
	}
	
	public Listening build() {
		final SslManager sslManager = new SslManager(trust, false, executor, byteBufferAllocator);
		final Listening thisListening = listening;
		return new Listening() {
			@Override
			public void connecting(Connector connector, SocketBuilder builder) {
				sslManager.connector = connector;
		
				InnerSocketBuilder innerSocketBuilder = new InnerSocketBuilder();
				
				thisListening.connecting(sslManager, innerSocketBuilder);
		
				sslManager.connecting = innerSocketBuilder.connecting;
				sslManager.closing = innerSocketBuilder.closing;
				sslManager.failing = innerSocketBuilder.failing;
				sslManager.receiver = innerSocketBuilder.receiver;
				
				builder.closing(sslManager);
				builder.connecting(sslManager);
				builder.failing(sslManager);
				builder.receiving(sslManager);
			}
		};
	}
	
	private static final class InnerSocketBuilder implements SocketBuilder {
		public Receiver receiver = null;
		public Failing failing = null;
		public Connecting connecting = null;
		public Closing closing = null;
		public InnerSocketBuilder() {
		}
		@Override
		public SocketBuilder receiving(Receiver receiver) {
			this.receiver = receiver;
			return this;
		}
		@Override
		public SocketBuilder failing(Failing failing) {
			this.failing = failing;
			return this;
		}
		@Override
		public SocketBuilder connecting(Connecting connecting) {
			this.connecting = connecting;
			return this;
		}
		@Override
		public SocketBuilder closing(Closing closing) {
			this.closing = closing;
			return this;
		}
	}

}
