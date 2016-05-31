package com.davfx.ninio.telnet.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.TcpSocket;

public final class TelnetClient {
	public static interface Builder extends NinioBuilder<Connector> {
		Builder with(TcpSocket.Builder builder);
		Builder receiving(Receiver receiver);
	}

	public static Builder builder() {
		return new Builder() {
			private Receiver receiver = null;
			private TcpSocket.Builder builder = null;
			
			@Override
			public Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public Builder with(TcpSocket.Builder builder) {
				this.builder = builder;
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				if (builder == null) {
					throw new NullPointerException("builder");
				}
				
				final Receiver r = receiver;
				final TelnetReader telnetReader = new TelnetReader();
				return builder.receiving(new Receiver() {
					@Override
					public void received(Connector connector, Address address, ByteBuffer buffer) {
						telnetReader.handle(buffer, r, connector);
					}
				}).create(queue);
			}
		};
	}
	
	private TelnetClient() {
	}
}
