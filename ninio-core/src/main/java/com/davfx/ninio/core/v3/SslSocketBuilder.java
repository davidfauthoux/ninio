package com.davfx.ninio.core.v3;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;

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
	
	public TcpSocket.Builder trust(Trust trust) {
		this.trust = trust;
		return this;
	}
	
	@Override
	public TcpSocket.Builder closing(Closing closing) {
		this.closing = closing;
		return this;
	}

	@Override
	public TcpSocket.Builder connecting(Connecting connecting) {
		this.connecting = connecting;
		return this;
	}
	
	@Override
	public TcpSocket.Builder failing(Failing failing) {
		this.failing = failing;
		return this;
	}
	
	@Override
	public TcpSocket.Builder receiving(Receiver receiver) {
		this.receiver = receiver;
		return this;
	}
	
	@Override
	public TcpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
		this.byteBufferAllocator = byteBufferAllocator;
		return this;
	}

	@Override
	public TcpSocket.Builder to(Address connectAddress) {
		this.connectAddress = connectAddress;
		return this;
	}
	
	//TODO executor
	@Override
	public Connector create(Queue queue) {
		SslManager sslManager = new SslManager(trust, byteBufferAllocator);
		sslManager.connecting = connecting;
		sslManager.closing = closing;
		sslManager.failing = failing;
		sslManager.receiver = receiver;

		sslManager.connector = wrappee
			.with(byteBufferAllocator)
			.connecting(sslManager)
			.receiving(sslManager)
			.closing(sslManager)
			.failing(sslManager)
			.to(connectAddress)
			.create(queue);

		return sslManager;
	}
}
