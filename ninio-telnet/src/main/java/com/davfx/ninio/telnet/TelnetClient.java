package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.DefaultByteBufferAllocator;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.util.Mutable;

public final class TelnetClient {
	/*%%
	public static void main(String[] args) throws Exception {
		try (Ninio ninio = Ninio.create()) {
			Connector c = ninio.create(TelnetClient.builder().receiving(new Receiver() {
				private int n = 0;
				@Override
				public void received(Address address, ByteBuffer buffer) {
					System.out.println(n + " ---> "+ new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), Charsets.UTF_8));
					switch (n) {
					case 1:
						connector.send(null, ByteBuffer.wrap(("davidfauthoux" + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)));
						break;
					case 3:
						connector.send(null, ByteBuffer.wrap(("mypassword" + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)));
						break;
					case 5:
						connector.send(null, ByteBuffer.wrap(("ls" + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)));
						break;
					}
					n++;
				}
			}).with(TcpSocket.builder().to(new Address(Address.LOCALHOST, TelnetSpecification.DEFAULT_PORT))));
			try {
				Thread.sleep(100000);
			} finally {
				c.close();
			}
		}
	}
	*/
	
	public static interface Builder extends TcpSocket.Builder {
		Builder with(TcpSocket.Builder builder);
		Builder with(Executor executor);
	}

	public static Builder builder() {
		return new Builder() {
			private TcpSocket.Builder builder = TcpSocket.builder();
			
			private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();
			private Address bindAddress = null;
			private Address connectAddress = null;
			
			private Executor executor = null;

			@Override
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder bind(Address bindAddress) {
				this.bindAddress = bindAddress;
				return this;
			}
			
			@Override
			public Builder with(ByteBufferAllocator byteBufferAllocator) {
				this.byteBufferAllocator = byteBufferAllocator;
				return this;
			}
			
			@Override
			public Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}

			@Override
			public Builder with(TcpSocket.Builder builder) {
				this.builder = builder;
				return this;
			}
			
			@Override
			public Connecter create(Queue queue) {
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				
				final Connecter connecter = builder.to(connectAddress).bind(bindAddress).with(byteBufferAllocator).create(queue);
				final Executor thisExecutor = executor;

				return new Connecter() {
					@Override
					public Connecter.Connecting connect(final Callback callback) {
						final TelnetReader telnetReader = new TelnetReader();

						final Mutable<Connecter.Connecting> connecting = new Mutable<>();
						
						final Connecter.Connecting c = connecter.connect(new Connecter.Callback() {
							@Override
							public void closed() {
								callback.closed();
							}
							@Override
							public void connected(Address address) {
								callback.connected(address);
							}
							@Override
							public void failed(IOException ioe) {
								callback.failed(ioe);
							}
							
							@Override
							public void received(Address address, ByteBuffer buffer) {
								telnetReader.handle(buffer, callback, new Connecting() {
									@Override
									public void send(final Address address, final ByteBuffer buffer, final Callback callback) {
										thisExecutor.execute(new Runnable() {
											@Override
											public void run() {
												connecting.value.send(address, buffer, callback);
											}
										});
									}
									
									@Override
									public void close() {
										thisExecutor.execute(new Runnable() {
											@Override
											public void run() {
												connecting.value.close();
											}
										});
									}
								});
							}
						});
						
						executor.execute(new Runnable() {
							@Override
							public void run() {
								connecting.value = c;
							}
						});
						
						return c;
					}
				};
			}
		};
	}
	
	private TelnetClient() {
	}
}
