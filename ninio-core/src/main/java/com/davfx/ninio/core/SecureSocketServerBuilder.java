package com.davfx.ninio.core;

import java.io.IOException;
import java.util.concurrent.Executor;

public final class SecureSocketServerBuilder implements TcpSocketServer.Builder {
	private Trust trust = new Trust();
	private Executor executor = null;
	private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator(SecureSocketManager.REQUIRED_BUFFER_SIZE);

	private Address bindAddress = null;
	
	private final TcpSocketServer.Builder wrappee;

	public SecureSocketServerBuilder(TcpSocketServer.Builder wrappee) {
		this.wrappee = wrappee;
	}
	
	public SecureSocketServerBuilder trust(Trust trust) {
		this.trust = trust;
		return this;
	}
	
	public SecureSocketServerBuilder with(Executor executor) {
		this.executor = executor;
		return this;
	}

	// Used for the SSL engine AND the underlying TcpSocketServer.Builder
	@Override
	public SecureSocketServerBuilder with(ByteBufferAllocator byteBufferAllocator) {
		this.byteBufferAllocator = byteBufferAllocator;
		return this;
	}

	@Override
	public SecureSocketServerBuilder bind(Address bindAddress) {
		this.bindAddress = bindAddress;
		return this;
	}

	@Override
	public Listener create(Queue queue) {
		final Trust thisTrust = trust;
		final Executor thisExecutor = executor;
		final ByteBufferAllocator thisByteBufferAllocator = byteBufferAllocator;
		final Listener listener = wrappee.with(byteBufferAllocator).bind(bindAddress).create(queue);
		
		return new Listener() {
			@Override
			public void listen(final Listening callback) {
				listener.listen(new Listening() {
					
					@Override
					public void failed(IOException ioe) {
						callback.failed(ioe);
					}
					
					@Override
					public void connected(Address address) {
						callback.connected(address);
					}
					
					@Override
					public void closed() {
						callback.closed();
					}

					@Override
					public Connection connecting(Connected connecting) {
						SecureSocketManager sslManager = new SecureSocketManager(thisTrust, false, thisExecutor, thisByteBufferAllocator);
						sslManager.prepare(null, connecting);
						sslManager.prepare(callback.connecting(sslManager));
						return sslManager;
					}
				});
			}
			
			@Override
			public void close() {
				listener.close();
			}
		};
	}
}
