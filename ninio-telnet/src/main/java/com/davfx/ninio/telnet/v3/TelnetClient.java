package com.davfx.ninio.telnet.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Ninio;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.TcpSocket;
import com.google.common.base.Charsets;

public final class TelnetClient {
	public static void main(String[] args) throws Exception {
		try (Ninio ninio = Ninio.create()) {
			Connector c = ninio.create(TelnetClient.builder().receiving(new Receiver() {
				private int n = 0;
				@Override
				public void received(Connector connector, Address address, ByteBuffer buffer) {
					System.out.println(n + " ---> "+ new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining(), Charsets.UTF_8));
					switch (n) {
					case 1:
						connector.send(null, ByteBuffer.wrap(("davidfauthoux" + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)));
						break;
					case 3:
						connector.send(null, ByteBuffer.wrap(("password" + TelnetSpecification.EOL).getBytes(Charsets.UTF_8)));
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
