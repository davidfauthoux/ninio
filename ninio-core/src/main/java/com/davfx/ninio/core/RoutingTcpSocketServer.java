package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Supplier;

public final class RoutingTcpSocketServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(RoutingTcpSocketServer.class);

	public static interface Builder extends NinioBuilder<Disconnectable> {
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
			public Disconnectable create(final Queue queue) {
				if (serverBuilder == null) {
					throw new NullPointerException("serverBuilder");
				}
				if (clientBuilderSupplier == null) {
					throw new NullPointerException("clientBuilderSupplier");
				}

				final Supplier<TcpSocket.Builder> supplier = clientBuilderSupplier;
				return serverBuilder.listening(new Listening() {
					@Override
					public Connection connecting(Address from, final Connector conn) {
						TcpSocket.Builder builder = supplier.get();
						
						builder.closing(new Closing() {
							@Override
							public void closed() {
								conn.close();
							}
						});
						builder.failing(new Failing() {
							@Override
							public void failed(IOException e) {
								LOGGER.warn("Failed to route", e);
								conn.close();
							}
						});
						builder.receiving(new Receiver() {
							@Override
							public void received(Connector c, Address address, ByteBuffer buffer) {
								conn.send(address, buffer);
							}
						});
						
						final Connector socket = builder.create(queue);
						
						return new Connection() {
							@Override
							public Connecting connecting() {
								return null;
							}

							@Override
							public Closing closing() {
								return new Closing() {
									@Override
									public void closed() {
										socket.close();
									}
								};
							}
							@Override
							public Failing failing() {
								return new Failing() {
									@Override
									public void failed(IOException e) {
										socket.close();
									}
								};
							}

							@Override
							public Receiver receiver() {
								return new Receiver() {
									@Override
									public void received(Connector c, Address address, ByteBuffer buffer) {
										socket.send(address, buffer);
									}
								};
							}
						};
					}
				}).create(queue);
			}
		};
	}
}
