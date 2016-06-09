package com.davfx.ninio.telnet.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.v3.Address;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Listening;
import com.davfx.ninio.core.v3.Ninio;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.TcpSocketServer;

public final class TelnetServer {
	
	public static void main(String[] args) throws Exception {
		try (Ninio ninio = Ninio.create()) {
			Disconnectable ss = ninio.create(TelnetServer.builder().listening(new Listening() {
				@Override
				public Connection connecting(Address from, final Connector connector) {
					return new Listening.Connection() {
						@Override
						public Receiver receiver() {
							return new Receiver() {
								@Override
								public void received(Address address, ByteBuffer buffer) {
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
			}).with(TcpSocketServer.builder().bind(new Address(Address.ANY, 8080))));
			try {
				Thread.sleep(100000);
			} finally {
				ss.close();
			}
		}
	}
	
	public static interface Builder extends NinioBuilder<Disconnectable> {
		Builder with(TcpSocketServer.Builder builder);
		Builder listening(Listening listening);
	}

	public static Builder builder() {
		return new Builder() {
			private Listening listening = null;
			private TcpSocketServer.Builder builder = null;
			
			@Override
			public Builder listening(Listening listening) {
				this.listening = listening;
				return this;
			}
			
			@Override
			public Builder with(TcpSocketServer.Builder builder) {
				this.builder = builder;
				return this;
			}

			@Override
			public Disconnectable create(Queue queue) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}
				
				final Listening l = listening;
				final TelnetReader telnetReader = new TelnetReader();
				return builder.listening((l == null) ? null : new Listening() {
					@Override
					public Connection connecting(Address from, final Connector connector) {
						Connection c = l.connecting(from, connector);
						final Receiver connectionReceiver = c.receiver();
						final Closing connectionClosing = c.closing();
						final Connecting connectionConnecting = c.connecting();
						final Failing connectionFailing = c.failing();
						return new Listening.Connection() {
							@Override
							public Receiver receiver() {
								return new Receiver() {
									@Override
									public void received(Address address, ByteBuffer buffer) {
										telnetReader.handle(buffer, connectionReceiver, connector);
									}
								};
							}
							
							@Override
							public Failing failing() {
								return connectionFailing;
							}
							@Override
							public Connecting connecting() {
								return connectionConnecting;
							}
							@Override
							public Closing closing() {
								return connectionClosing;
							}
						};
					}
				}).create(queue);
			}
		};
	}
	
	private TelnetServer() {
	}
}
