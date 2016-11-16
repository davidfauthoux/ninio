package com.davfx.ninio.core;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

public final class SecureSocketBuilder implements TcpSocket.Builder {
	private Trust trust = new Trust();
	private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator(SecureSocketManager.REQUIRED_BUFFER_SIZE);

	private Address bindAddress = null;
	
	private Address connectAddress = null;
	
	private final TcpSocket.Builder wrappee;

	public SecureSocketBuilder(TcpSocket.Builder wrappee) {
		this.wrappee = wrappee;
	}

	public SecureSocketBuilder trust(Trust trust) {
		this.trust = trust;
		return this;
	}
	
	@Deprecated
	public SecureSocketBuilder with(Executor executor) {
		return this;
	}
	
	@Override
	public SecureSocketBuilder with(ByteBufferAllocator byteBufferAllocator) {
		this.byteBufferAllocator = byteBufferAllocator;
		return this;
	}
	
	@Override
	public SecureSocketBuilder bind(Address bindAddress) {
		this.bindAddress = bindAddress;
		return this;
	}

	@Override
	public SecureSocketBuilder to(Address connectAddress) {
		this.connectAddress = connectAddress;
		return this;
	}

	@Override
	public Connecter create(NinioProvider ninioProvider) {
		final Connecter connecter = wrappee
			.with(byteBufferAllocator)
			.bind(bindAddress)
			.to(connectAddress)
			.create(ninioProvider);
		
		final SecureSocketManager sslManager = new SecureSocketManager(trust, true, ninioProvider.executor(), byteBufferAllocator);
		sslManager.prepare(connectAddress, connecter);

		return new Connecter() {
			@Override
			public void close() {
				sslManager.close();
			}
			
			@Override
			public void send(Address address, ByteBuffer buffer, SendCallback callback) {
				sslManager.send(address, buffer, callback);
			}
			
			@Override
			public void connect(Connection callback) {
				sslManager.prepare(callback);
				connecter.connect(sslManager);
			}
		};
	}
}
