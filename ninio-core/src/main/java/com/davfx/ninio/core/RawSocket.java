package com.davfx.ninio.core;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.SerialExecutor;

public final class RawSocket implements Connecter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RawSocket.class);

	private static final byte[] IPV6_LOCALHOST = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 };
	private static final int TERMINATE_CODE = Integer.MAX_VALUE;
	private static double CLOSE_STEP_TIME = 0.01d;
	private static int CLOSE_NUMBER_OF_STEPS_BEFORE_SENDING_AGAIN = 100;

	public static interface Builder extends NinioBuilder<Connecter> {
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
			public Connecter create(NinioProvider ninioProvider) {
				return new RawSocket(family, protocol, bindAddress);
			}
		};
	}
	
	private final ProtocolFamily family;
	private final int protocol;
	private final Address bindAddress;
	
	private final Executor loop = new SerialExecutor(RawSocket.class);

	private NativeRawSocket socket = null;
	private boolean closed = false;
	
	private boolean actuallyClosed = false;
	private final Object actuallyClosedLock = new Object();

	private RawSocket(ProtocolFamily family, int protocol, Address bindAddress) {
		this.family = family;
		this.protocol = protocol;
		this.bindAddress = bindAddress;
	}
	
	@Override
	public void connect(final Connection callback) {
		if (socket != null) {
			throw new IllegalStateException("connect() cannot be called twice");
		}

		if (closed) {
			callback.failed(new IOException("Closed"));
			return;
		}
		
		final NativeRawSocket s;
		try {
			s = new NativeRawSocket((family == StandardProtocolFamily.INET) ? NativeRawSocket.PF_INET : NativeRawSocket.PF_INET6, protocol);
		} catch (Exception ee) {
			closed = true;
			callback.failed(new IOException("Failed to be created", ee));
			return;
		}
		
		if (bindAddress != null) {
			try {
				s.bind(bindAddress.ip);
			} catch (Exception ee) {
				try {
					s.close();
				} catch (Exception ce) {
				}
				closed = true;
				callback.failed( new IOException("Could not bind to: " + bindAddress, ee));
				return;
			}
		}
		
		socket = s;

		loop.execute(new Runnable() {
			@Override
			public void run() {
				callback.connected(null);
				
				while (true) {
					byte[] recvData = new byte[84];
					byte[] srcAddress = new byte[(family == StandardProtocolFamily.INET) ? 4 : 16];
					try {
						LOGGER.debug("Reading");
						int r = s.read(recvData, 0, recvData.length, srcAddress);

						final ByteBuffer b = ByteBuffer.wrap(recvData, 0, r);
						if (family == StandardProtocolFamily.INET) {
							int headerLength = (b.get() & 0x0F) * 4;
							b.position(headerLength);
						}

						byte[] localhostIp;
						if (family == StandardProtocolFamily.INET) {
							localhostIp = Address.LOCALHOST;
						} else {
							localhostIp = IPV6_LOCALHOST;
						}
						if (Arrays.equals(srcAddress, localhostIp)) {
							int type;
							int code;
							int id;
							try {
								type = b.get() & 0xFF; // type
								code = b.get() & 0xFF; // code
								if ((type != 0) || (code != 0)) {
									continue;
								}
								b.getShort(); // checksum
								short identifier = b.getShort(); // identifier
								short sequence = b.getShort(); // sequence
								b.getLong(); // time
								id = (int) (((identifier & 0xFFFFL) << 16) | (sequence & 0xFFFFL));
							} catch (Exception e) {
								LOGGER.error("Invalid packet", e);
								continue;
							}
							
							if (id == TERMINATE_CODE) {
								LOGGER.debug("Received terminate packet");
								break;
							}
							continue;
						}
						
						Address a = new Address(srcAddress, 0);
						LOGGER.debug("Received raw packet: {} bytes from: {}", r, a);

						callback.received(a, b);
					} catch (Exception e) {
						LOGGER.error("Error while reading", e);
						break;
					}
				}

				try {
					s.close();
				} catch (IOException e) {
				}
				LOGGER.debug("Closed");
				synchronized (actuallyClosedLock) {
					actuallyClosed = true;
					actuallyClosedLock.notifyAll();
				}
				callback.closed();
			}
		});
	}
	
	@Override
	public void send(Address address, ByteBuffer buffer, final SendCallback callback) {
		if (socket == null) {
			throw new IllegalStateException("send() must be called after connect()");
		}
		try {
			
			while (buffer.hasRemaining()) {
				LOGGER.debug("Writing");
				int r = socket.write(address.ip, buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
				if (r == 0) {
					throw new IOException("Error writing to: " + address);
				}
				buffer.position(buffer.position() + r);
			}

			callback.sent();
		} catch (Exception e) {
			callback.failed(new IOException("Could not write", e));
		}
	}
	
	@Override
	public void close() {
		try {
			if (closed) {
				return;
			}
			closed = true;
			
			if (socket != null) {
				byte[] localhostIp;
				if (family == StandardProtocolFamily.INET) {
					localhostIp = Address.LOCALHOST;
				} else {
					localhostIp = IPV6_LOCALHOST;
				}
				
				int id = TERMINATE_CODE;
				
				byte[] sendData = new byte[16];
				ByteBuffer b = ByteBuffer.wrap(sendData);
				b.put((byte) 8); // requestType (Echo)
				b.put((byte) 0); // code
				int checksumPosition = b.position();
				b.putShort((short) 0); // checksum
				b.putShort((short) ((id >>> 16) & 0xFFFF)); // identifier
				b.putShort((short) (id & 0xFFFF)); // sequence
				long nt = System.nanoTime();
				b.putLong(nt);
				int endPosition = b.position();

				b.position(0);
				int checksum = 0;
				while (b.position() < endPosition) {
					checksum += b.getShort() & 0xFFFF;
				}
				while((checksum & 0xFFFF0000) != 0) {
					checksum = (checksum & 0xFFFF) + (checksum >>> 16);
				}

				checksum = (~checksum & 0xffff);
				b.position(checksumPosition);
				b.putShort((short) (checksum & 0xFFFF));
				b.position(endPosition);
				b.flip();

				while (true) {
					LOGGER.debug("Sending terminate packet");
					int r = socket.write(localhostIp, b.array(), b.arrayOffset() + b.position(), b.remaining());
					if (r == 0) {
						LOGGER.error("Could not send terminate packet");
						socket.close();
						break;
					}

					synchronized (actuallyClosedLock) {
						int count = 0;
						while (!actuallyClosed) {
							if (count == CLOSE_NUMBER_OF_STEPS_BEFORE_SENDING_AGAIN) {
								break;
							}
							try {
								actuallyClosedLock.wait((long) (CLOSE_STEP_TIME * 1000L));
							} catch (InterruptedException ie) {
							}
							count++;
						}
						if (actuallyClosed) {
							break;
						}
					}
				}
			}
		} catch (IOException e) {
			LOGGER.error("Could not close", e);
		}
	}
}
