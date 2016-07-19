package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;

public final class RoutingTcpSocketServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(RoutingTcpSocketServer.class);

	public static interface Builder extends NinioBuilder<Listener> {
		Builder serve(TcpSocketServer.Builder serverBuilder);
		Builder to(Supplier<TcpSocket.Builder> clientBuilderSupplier);
	}

	public static Builder builder() {
		return new Builder() {
			private TcpSocketServer.Builder serverBuilder = null;
			private Supplier<TcpSocket.Builder> clientBuilderSupplier = null;
			
			@Override
			public Builder serve(TcpSocketServer.Builder serverBuilder) {
				this.serverBuilder = serverBuilder;
				return this;
			}
			
			@Override
			public Builder to(Supplier<TcpSocket.Builder> clientBuilderSupplier) {
				this.clientBuilderSupplier = clientBuilderSupplier;
				return this;
			}
			
			@Override
			public Listener create(final Queue queue) {
				if (serverBuilder == null) {
					throw new NullPointerException("serverBuilder");
				}
				if (clientBuilderSupplier == null) {
					throw new NullPointerException("clientBuilderSupplier");
				}

				final Supplier<TcpSocket.Builder> supplier = clientBuilderSupplier;
				final Listener listener = serverBuilder.create(queue);

				return new Listener() {
					
					@Override
					public void close() {
						listener.close();
					}
					
					@Override
					public void listen(Listening callback) {
						listener.listen(new Listening() {
							@Override
							public Connection connecting(final Connected connecting) {
								final Connecter connecter = supplier.get().create(queue);
								
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
							}
							
							@Override
							public void failed(IOException e) {
								LOGGER.warn("Failed to listen", e);
							}
							
							@Override
							public void closed() {
							}
						});
					}
				};
			}
		};
	}
}
