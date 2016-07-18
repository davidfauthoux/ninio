package com.davfx.ninio.core;

import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

public final class SecureSocketBuilder implements TcpSocket.Builder {
	private Trust trust = new Trust();
	private Executor executor = null;
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
	
	public SecureSocketBuilder with(Executor executor) {
		this.executor = executor;
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
	public Connecter create(final Queue queue) {
		final Connecter connecter = wrappee
			.with(byteBufferAllocator)
			.bind(bindAddress)
			.to(connectAddress)
			.create(queue);
		
		final SecureSocketManager sslManager = new SecureSocketManager(trust, true, executor, byteBufferAllocator);
		sslManager.connectAddress = connectAddress;
		sslManager.connecting = connecter;

		final Executor thisExecutor = executor;

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
			public void connect(final Connection callback) {
				thisExecutor.execute(new Runnable() {
					@Override
					public void run() {
						sslManager.callback = callback;
					}
				});

				connecter.connect(sslManager);
			}
		};
	}
}
