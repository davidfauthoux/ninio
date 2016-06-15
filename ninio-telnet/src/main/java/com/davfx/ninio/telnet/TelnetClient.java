package com.davfx.ninio.telnet;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.DefaultByteBufferAllocator;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Receiver;
import com.davfx.ninio.core.TcpSocket;

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
		Builder failing(Failing failing);
		Builder closing(Closing closing);
		Builder connecting(Connecting connecting);
		Builder receiving(Receiver receiver);
		Builder to(Address connectAddress);
		Builder with(ByteBufferAllocator byteBufferAllocator);
		Builder bind(Address bindAddress);
	}

	public static Builder builder() {
		return new Builder() {
			private Receiver receiver = null;
			private Closing closing = null;
			private Failing failing = null;
			private Connecting connecting = null;
			private TcpSocket.Builder builder = TcpSocket.builder();
			
			private ByteBufferAllocator byteBufferAllocator = new DefaultByteBufferAllocator();
			private Address bindAddress = null;
			private Address connectAddress = null;
			
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
			public Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public Builder receiving(Receiver receiver) {
				this.receiver = receiver;
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
			public Connector create(Queue queue) {
				final Receiver r = receiver;
				final TelnetReader telnetReader = new TelnetReader();

				final InnerHolder innerHolder = new InnerHolder();
				innerHolder.connector = builder
						.failing(failing)
						.connecting(connecting)
						.closing(closing)
						.receiving(new Receiver() {
							@Override
							public void received(Address address, ByteBuffer buffer) {
								telnetReader.handle(buffer, r, innerHolder.connector);
							}
						})
						.to(connectAddress)
						.bind(bindAddress)
						.with(byteBufferAllocator)
						.create(queue);
				return innerHolder.connector;
			}
		};
	}
	
	private static final class InnerHolder {
		public Connector connector;
	}
	
	private TelnetClient() {
	}
}
