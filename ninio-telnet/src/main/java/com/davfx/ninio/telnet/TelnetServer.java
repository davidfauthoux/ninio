package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connected;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.TcpSocketServer;

public final class TelnetServer {
	/*%%
	public static void main(String[] args) throws Exception {
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable ss = ninio.create(TelnetServer.builder().listening(new Listening() {
				@Override
				public Connection connecting(Address from, final Connector connector) {
					return new Listening.Connection() {
						@Override
						public Receiver receiver() {
							return new Receiver() {
								@Override
								public void received(Connector conn, Address address, ByteBuffer buffer) {
									connector.send(null, buffer);
								}
							};
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
			}).with(TcpSocketServer.builder().bind(new Address(Address.ANY, 8080))))) {
				Thread.sleep(60000);
			}
		}
	}*/
	
	public static interface Builder extends NinioBuilder<Listener> {
		Builder with(TcpSocketServer.Builder builder);
	}

	public static Builder builder() {
		return new Builder() {
			private TcpSocketServer.Builder builder = null;
			
			@Override
			public Builder with(TcpSocketServer.Builder builder) {
				this.builder = builder;
				return this;
			}

			@Override
			public Listener create(Queue queue) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}
				
				final TelnetReader telnetReader = new TelnetReader();
				final Listener listener = builder.create(queue);
				
				return new Listener() {
					@Override
					public void close() {
						listener.close();
					}
					
					@Override
					public void listen(final Callback callback) {
						listener.listen(new Listener.Callback() {
							@Override
							public Connection connecting(final Connected connected) {
								final Connection c = callback.connecting(connected);
								return new Connection() {
									@Override
									public void received(Address address, ByteBuffer buffer) {
										telnetReader.handle(buffer, c, connected);
									}
									
									@Override
									public void closed() {
										c.closed();
									}
									
									@Override
									public void failed(IOException e) {
										c.failed(e);
									}
									
									@Override
									public void connected(Address address) {
										c.connected(address);
									}
								};
							}
							
							@Override
							public void closed() {
								callback.closed();
							}
							
							@Override
							public void failed(IOException e) {
								callback.failed(e);
							}
							
							@Override
							public void connected(Address address) {
								callback.connected(address);
							}
						});
					}
				};
			}
		};
	}
	
	private TelnetServer() {
	}
}
