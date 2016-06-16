package com.davfx.ninio.core;

import java.util.concurrent.Executor;

public final class SecureSocketServerBuilder {
	private Trust trust = new Trust();
	private Executor executor = null;
	private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator(SecureSocketManager.REQUIRED_BUFFER_SIZE);

	private Listening listening = new Listening() {
		@Override
		public Connection connecting(Address from, Connector connector) {
			connector.close();
			return new Listening.Connection() {
				@Override
				public Receiver receiver() {
					return null;
				}
				@Override
				public Failing failing() {
					return null;
				}
				@Override
				public Connecting connecting() {
					return null;
				}
				@Override
				public Closing closing() {
					return null;
				}
			};
		}
	};

	public SecureSocketServerBuilder() {
	}
	
	public SecureSocketServerBuilder trust(Trust trust) {
		this.trust = trust;
		return this;
	}
	public SecureSocketServerBuilder with(Executor executor) {
		this.executor = executor;
		return this;
	}
	public SecureSocketServerBuilder with(ByteBufferAllocator byteBufferAllocator) {
		this.byteBufferAllocator = byteBufferAllocator;
		return this;
	}
	
	public SecureSocketServerBuilder listening(Listening listening) {
		this.listening = listening;
		return this;
	}
	
	public Listening build() {
		final Trust thisTrust = trust;
		final Executor thisExecutor = executor;
		final ByteBufferAllocator thisByteBufferAllocator = byteBufferAllocator;
		final Listening thisListening = listening;
		return new Listening() {
			@Override
			public Listening.Connection connecting(final Address from, Connector connector) {
				final SecureSocketManager sslManager = new SecureSocketManager(thisTrust, false, thisExecutor, thisByteBufferAllocator);
				sslManager.connectAddress = from;
				sslManager.connector = connector;

				thisExecutor.execute(new Runnable() {
					@Override
					public void run() {
						Listening.Connection connection = thisListening.connecting(from, sslManager);

						sslManager.connecting = connection.connecting();
						sslManager.closing = connection.closing();
						sslManager.failing = connection.failing();
						sslManager.receiver = connection.receiver();
					}
				});
				
				return new Listening.Connection() {
					@Override
					public Receiver receiver() {
						return sslManager;
					}
					@Override
					public Failing failing() {
						return sslManager;
					}
					@Override
					public Connecting connecting() {
						return sslManager;
					}
					@Override
					public Closing closing() {
						return sslManager;
					}
				};
			}
		};
	}
}
