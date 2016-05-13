package com.davfx.ninio.ping.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;

public final class PingClient implements Disconnectable {
	public static interface Builder extends NinioBuilder<PingClient> {
		Builder receiving(PingReceiver receiver);
		Builder with(PingSocket.Builder connectorFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private PingReceiver receiver = null;
			private PingSocket.Builder connectorFactory = PingSocket.builder();
			
			@Override
			public Builder receiving(PingReceiver receiver) {
				this.receiver = receiver;
				return this;
			}

			@Override
			public Builder with(PingSocket.Builder connectorFactory) {
				this.connectorFactory = connectorFactory;
				return this;
			}

			@Override
			public PingClient create(Queue queue) {
				return new PingClient(queue, connectorFactory, receiver);
			}
		};
	}
	
	private final Connector connector;

	public PingClient(Queue queue, PingSocket.Builder connectorFactory, final PingReceiver receiver) {
		connector = connectorFactory.receiving(new Receiver() {
			@Override
			public void received(Connector connector, Address address, ByteBuffer buffer) {
				if (receiver != null) {
					receiver.received(address.getHost(), buffer.getDouble());
				}
			}
		}).create(queue);
	}
	
	@Override
	public void close() {
		connector.close();
	}
	
	
	public void ping(String host) {
		connector.send(new Address(host, 0), ByteBuffer.allocate(0));
	}
}
