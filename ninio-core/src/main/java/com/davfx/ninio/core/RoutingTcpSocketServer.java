package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RoutingTcpSocketServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(RoutingTcpSocketServer.class);

	public interface RoutingListener extends Disconnectable {
		void listen(ConnectingClosingFailing listening);
	}

	public static interface Builder extends NinioBuilder<RoutingListener> {
		Builder serve(TcpSocketServer.Builder serverBuilder);
		Builder to(TcpSocket.Builder clientBuilder);
	}

	public static Builder builder() {
		return new Builder() {
			private TcpSocketServer.Builder serverBuilder = null;
			private TcpSocket.Builder clientBuilder = null;
			
			@Override
			public Builder serve(TcpSocketServer.Builder serverBuilder) {
				this.serverBuilder = serverBuilder;
				return this;
			}
			
			@Override
			public Builder to(TcpSocket.Builder clientBuilder) {
				this.clientBuilder = clientBuilder;
				return this;
			}
			
			@Override
			public RoutingListener create(final NinioProvider ninioProvider) {
				if (serverBuilder == null) {
					throw new NullPointerException("serverBuilder");
				}
				if (clientBuilder == null) {
					throw new NullPointerException("clientBuilder");
				}

				final TcpSocket.Builder supplier = clientBuilder;
				final Listener listener = serverBuilder.create(ninioProvider);
				
				return new RoutingListener() {
					@Override
					public void close() {
						listener.close();
					}
					@Override
					public void listen(final ConnectingClosingFailing listening) {
						listener.listen(new Listening() {
							@Override
							public Connection connecting(final Connected connecting) {
								final Connecter connecter = supplier.create(ninioProvider);
								
								connecter.connect(new Connection() {
									@Override
									public void connected(Address address) {
									}
									
									@Override
									public void received(Address address, ByteBuffer buffer) {
										connecting.send(address, buffer, new SendCallback() {
											@Override
											public void failed(IOException e) {
												LOGGER.warn("Failed to route packet", e);
												connecting.close();
												connecter.close();
											}
											@Override
											public void sent() {
											}
										});
									}
									
									@Override
									public void closed() {
										connecting.close();
									}
									
									@Override
									public void failed(IOException e) {
										LOGGER.warn("Failed to route", e);
										connecting.close();
									}
								});

								return new Connection() {
									@Override
									public void connected(Address address) {
									}
									
									@Override
									public void received(Address address, ByteBuffer buffer) {
										connecter.send(address, buffer, new SendCallback() {
											@Override
											public void failed(IOException e) {
												LOGGER.warn("Failed to route packet", e);
												connecting.close();
												connecter.close();
											}
											@Override
											public void sent() {
											}
										});
									}
									
									@Override
									public void closed() {
										connecter.close();
									}
									@Override
									public void failed(IOException e) {
										LOGGER.warn("Failed to route", e);
										connecter.close();
									}
								};
							}
							
							@Override
							public void connected(Address address) {
								listening.connected(address);
							}
							
							@Override
							public void failed(IOException e) {
								listening.failed(e);
							}
							
							@Override
							public void closed() {
								listening.closed();
							}
						});
					}
				};
			}
		};
	}
}
