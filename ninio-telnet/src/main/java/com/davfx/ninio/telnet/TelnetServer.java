package com.davfx.ninio.telnet;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Buffering;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Receiver;
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
				Listener listener = builder.create(queue);
				listener.listen((l == null) ? null : new Listener.Callback() {
					@Override
					public Listener.ListenerConnecting connecting() {
						Listener.ListenerConnecting c = l.connecting(from, connector);
						final Receiver connectionReceiver = c.receiver();
						final Closing connectionClosing = c.closing();
						final Connecting connectionConnecting = c.connecting();
						final Failing connectionFailing = c.failing();
						final Buffering connectionBuffering = c.buffering();
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
							@Override
							public Buffering buffering() {
								return connectionBuffering;
							}
						};
					}
				});
				return listener;
			}
		};
	}
	
	private TelnetServer() {
	}
}
