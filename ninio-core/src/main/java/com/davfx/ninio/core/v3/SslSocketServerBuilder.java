package com.davfx.ninio.core.v3;

import java.util.concurrent.Executor;

import com.davfx.ninio.core.Address;

public final class SslSocketServerBuilder {
	private Trust trust = new Trust();
	private Executor executor = null;
	private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();

	private Listening listening = new Listening() {
		@Override
		public void connecting(Address from, Connector connector, SocketBuilder<?> builder) {
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
			public void connecting(Address from, Connector connector, SocketBuilder<?> builder) {
				sslManager.connector = connector;
		
				InnerSocketBuilder innerSocketBuilder = new InnerSocketBuilder();
				
				thisListening.connecting(from, sslManager, innerSocketBuilder);
		
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
	
	private static final class InnerSocketBuilder implements SocketBuilder<Void> {
		public Receiver receiver = null;
		public Failing failing = null;
		public Connecting connecting = null;
		public Closing closing = null;
		public InnerSocketBuilder() {
		}
		@Override
		public Void receiving(Receiver receiver) {
			this.receiver = receiver;
			return null;
		}
		@Override
		public Void failing(Failing failing) {
			this.failing = failing;
			return null;
		}
		@Override
		public Void connecting(Connecting connecting) {
			this.connecting = connecting;
			return null;
		}
		@Override
		public Void closing(Closing closing) {
			this.closing = closing;
			return null;
		}
	}

}
