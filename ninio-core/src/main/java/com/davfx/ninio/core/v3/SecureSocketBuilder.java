package com.davfx.ninio.core.v3;

import java.util.concurrent.Executor;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.TcpSocket.Builder;

public final class SecureSocketBuilder implements TcpSocket.Builder {
	private Trust trust = new Trust();
	private Executor executor = null;
	private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();

	private Address bindAddress = null;
	
	private Address connectAddress = null;
	
	private Connecting connecting = null;
	private Closing closing = null;
	private Failing failing = null;
	private Receiver receiver = null;
	
	private final TcpSocket.Builder wrappee;

	public SecureSocketBuilder(TcpSocket.Builder wrappee) {
		this.wrappee = wrappee;
	}
	
	public SecureSocketBuilder trust(Trust trust) {
		this.trust = trust;
		return this;
	}
	
	public SecureSocketBuilder with(Executor executor) {
		this.executor = executor;
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
	public Builder bind(Address bindAddress) {
		this.bindAddress = bindAddress;
		return this;
	}

	@Override
	public TcpSocket.Builder to(Address connectAddress) {
		this.connectAddress = connectAddress;
		return this;
	}
	
	@Override
	public Connector create(Queue queue) {
		SecureSocketManager sslManager = new SecureSocketManager(trust, true, executor, byteBufferAllocator);
		sslManager.connectAddress = connectAddress;
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
			.bind(bindAddress)
			.to(connectAddress)
			.create(queue);

		return sslManager;
	}
}
