package com.davfx.ninio.core.v3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.v3.TcpSocket.Builder;

public final class SslSocketServerBuilder implements Listening {
	private static final Logger LOGGER = LoggerFactory.getLogger(SslSocketServerBuilder.class);

	private Trust trust = new Trust();
	private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();

	private final Listening wrappee;

	public SslSocketServerBuilder(Listening wrappee) {
		this.wrappee = wrappee;
	}
	
	public SslSocketServerBuilder trust(Trust trust) {
		this.trust = trust;
		return this;
	}
	public SslSocketServerBuilder with(ByteBufferAllocator byteBufferAllocator) {
		this.byteBufferAllocator = byteBufferAllocator;
		return this;
	}
	
	@Override
	public void connecting(Connector connector, SocketBuilder builder) {
		final SslManager sslManager = new SslManager(trust, byteBufferAllocator);
		sslManager.connector = connector;

		InnerSocketBuilder innerSocketBuilder = new InnerSocketBuilder();
		wrappee.connecting(sslManager, innerSocketBuilder);
		
		sslManager.connecting = innerSocketBuilder.connecting;
		sslManager.closing = innerSocketBuilder.closing;
		sslManager.failing = innerSocketBuilder.failing;
		sslManager.receiver = innerSocketBuilder.receiver;
		
		builder.closing(sslManager);
		builder.connecting(sslManager);
		builder.failing(sslManager);
		builder.receiving(sslManager);
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
