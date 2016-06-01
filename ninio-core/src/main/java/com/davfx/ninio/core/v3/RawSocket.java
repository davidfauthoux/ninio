package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.util.ClassThreadFactory;

public final class RawSocket implements Connector {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RawSocket.class);

	public static interface Builder extends NinioBuilder<Connector> {
		Builder family(ProtocolFamily family);
		Builder protocol(int protocol);
		Builder bind(Address bindAddress);
		Builder failing(Failing failing);
		Builder closing(Closing closing);
		Builder connecting(Connecting connecting);
		Builder receiving(Receiver receiver);
	}

	public static Builder builder() {
		return new Builder() {
			private ProtocolFamily family = StandardProtocolFamily.INET;
			private int protocol = 0;
			
			private Address bindAddress = null;

			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
			@Override
			public Builder family(ProtocolFamily family) {
				this.family = family;
				return this;
			}
			@Override
			public Builder protocol(int protocol) {
				this.protocol = protocol;
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
			public Builder bind(Address bindAddress) {
				this.bindAddress = bindAddress;
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				return new RawSocket(family, protocol, bindAddress, connecting, closing, failing, receiver);
			}
		};
	}
	
	private final NativeRawSocket socket;
	private final ExecutorService loop = Executors.newSingleThreadExecutor(new ClassThreadFactory(RawSocket.class));

	private RawSocket(final ProtocolFamily family, int protocol, Address bindAddress, final Connecting connecting, final Closing closing, final Failing failing, final Receiver receiver) {
		NativeRawSocket s;
		try {
			s = new NativeRawSocket((family == StandardProtocolFamily.INET) ? NativeRawSocket.PF_INET : NativeRawSocket.PF_INET6, protocol);
		} catch (Exception e) {
			if (failing != null) {
				failing.failed(new IOException("Error", e));
			}
			s = null;
		}
		
		socket = s;

		if (socket != null) {
		
			if (bindAddress != null) {
				try {
					InetAddress a = InetAddress.getByName(bindAddress.getHost()); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
					socket.bind(a);
				} catch (Exception e) {
					try {
						socket.close();
					} catch (Exception ee) {
					}
					if (failing != null) {
						failing.failed( new IOException("Could not bind to: " + bindAddress, e));
					}
				}
			}
			
			loop.execute(new Runnable() {
				@Override
				public void run() {
					if (connecting != null) {
						connecting.connected(null, RawSocket.this);
					}
					
					while (true) {
						byte[] recvData = new byte[84];
						byte[] srcAddress = new byte[(family == StandardProtocolFamily.INET) ? 4 : 16];
						try {
							int r = socket.read(recvData, 0, recvData.length, srcAddress);
							String host = InetAddress.getByAddress(srcAddress).getHostAddress();
							LOGGER.debug("Received raw packet: {} bytes from: {}", r, host);
							if (receiver != null) {
								ByteBuffer b = ByteBuffer.wrap(recvData, 0, r);
								if (family == StandardProtocolFamily.INET) {
									int headerLength = (b.get() & 0x0F) * 4;
									b.position(headerLength);
								}
								receiver.received(RawSocket.this, new Address(host, 0), b);
							}
						} catch (Exception e) {
							LOGGER.trace("Error, probably closed", e);
							break;
						}
					}
					
					if (closing != null) {
						closing.closed();
					}
				}
			});
			
		}
	}
	
	@Override
	public Connector send(Address address, ByteBuffer buffer) {
		if (socket != null) {
			try {
				socket.write(InetAddress.getByName(address.getHost()), buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
			} catch (Exception e) {
				LOGGER.error("Error", e);
			}
		}
		return this;
	}
	
	@Override
	public void close() {
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				LOGGER.trace("Error", e);
			}
		}
		loop.shutdown();
	}
}
