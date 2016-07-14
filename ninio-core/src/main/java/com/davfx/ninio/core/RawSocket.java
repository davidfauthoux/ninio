package com.davfx.ninio.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.SerialExecutor;

public final class RawSocket implements Connecter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RawSocket.class);

	public static interface Builder extends NinioBuilder<RawSocket> {
		Builder family(ProtocolFamily family);
		Builder protocol(int protocol);
		Builder bind(Address bindAddress);
	}

	public static Builder builder() {
		return new Builder() {
			private ProtocolFamily family = StandardProtocolFamily.INET;
			private int protocol = 0;
			
			private Address bindAddress = null;
			
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
			public Builder bind(Address bindAddress) {
				this.bindAddress = bindAddress;
				return this;
			}
			
			@Override
			public RawSocket create(Queue queue) {
				return new RawSocket(family, protocol, bindAddress);
			}
		};
	}
	
	private final ProtocolFamily family;
	private final int protocol;
	private final Address bindAddress;
	
	private final Executor loop = new SerialExecutor(RawSocket.class);

	private RawSocket(ProtocolFamily family, int protocol, Address bindAddress) {
		this.family = family;
		this.protocol = protocol;
		this.bindAddress = bindAddress;
	}
	
	@Override
	public Connecting connect(final Callback callback) {
		final NativeRawSocket socket;
		try {
			socket = new NativeRawSocket((family == StandardProtocolFamily.INET) ? NativeRawSocket.PF_INET : NativeRawSocket.PF_INET6, protocol);
		} catch (Exception ee) {
			callback.failed(new IOException("Error", ee));
			return new Connecting() {
				@Override
				public void close() {
				}
				@Override
				public void send(Address address, ByteBuffer buffer, Callback callback) {
					callback.failed(new IOException("Failed to be created"));
				}
			};
		}
		
		if (bindAddress != null) {
			try {
				InetAddress a = InetAddress.getByName(bindAddress.host); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
				socket.bind(a);
			} catch (Exception e) {
				try {
					socket.close();
				} catch (Exception ee) {
				}
				callback.failed( new IOException("Could not bind to: " + bindAddress, e));
				return new Connecting() {
					@Override
					public void close() {
					}
					@Override
					public void send(Address address, ByteBuffer buffer, Callback callback) {
						callback.failed(new IOException("Failed to bind"));
					}
				};
			}
		}
		
		loop.execute(new Runnable() {
			@Override
			public void run() {
				callback.connected(null);
				
				while (true) {
					byte[] recvData = new byte[84];
					byte[] srcAddress = new byte[(family == StandardProtocolFamily.INET) ? 4 : 16];
					try {
						int r = socket.read(recvData, 0, recvData.length, srcAddress);
						String host = InetAddress.getByAddress(srcAddress).getHostAddress();
						LOGGER.debug("Received raw packet: {} bytes from: {}", r, host);

						ByteBuffer b = ByteBuffer.wrap(recvData, 0, r);
						if (family == StandardProtocolFamily.INET) {
							int headerLength = (b.get() & 0x0F) * 4;
							b.position(headerLength);
						}
						callback.received(new Address(host, 0), b);
					} catch (Exception e) {
						LOGGER.trace("Error, probably closed", e);
						break;
					}
				}
				
				callback.closed();
			}
		});
		
		return new Connecting() {
			@Override
			public void send(Address address, ByteBuffer buffer, Callback callback) {
				try {
					socket.write(InetAddress.getByName(address.host), buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
					callback.sent();
				} catch (Exception e) {
					callback.failed(new IOException("Could not write", e));
				}
			}
			
			@Override
			public void close() {
				try {
					socket.close();
				} catch (IOException e) {
					LOGGER.error("Could not close", e);
				}
			}
		};
	}
}
