package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;
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
			public void listen(final Callback callback) {
				listener.listen(new Listener.Callback() {
					
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
					public Listener.ListenerConnecting connecting() {
						final SecureSocketManager sslManager = new SecureSocketManager(thisTrust, false, thisExecutor, thisByteBufferAllocator);

						final Listener.ListenerConnecting callbackConnecting = callback.connecting();
						
						sslManager.callback = new Connection() {
							@Override
							public void received(Address address, ByteBuffer buffer) {
								callbackConnecting.received(address, buffer);
							}
							
							@Override
							public void failed(IOException ioe) {
								callbackConnecting.failed(ioe);
							}
							
							@Override
							public void connected(Address address) {
								callbackConnecting.connected(address);
							}
							
							@Override
							public void closed() {
								callbackConnecting.closed();
							}
						};

						return new Listener.ListenerConnecting() {
							@Override
							public void connecting(Connected connecting) {
								sslManager.connecting = connecting;
								callbackConnecting.connecting(sslManager);
							}

							@Override
							public void connected(Address address) {
								sslManager.connectAddress = address;
							}
							
							@Override
							public void received(Address address, ByteBuffer buffer) {
								sslManager.received(address, buffer);
							}
							
							@Override
							public void failed(IOException ioe) {
								sslManager.failed(ioe);
							}
							
							@Override
							public void closed() {
								sslManager.closed();
							}
						};
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
