package com.davfx.ninio.core;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.LinkedList;
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
				return new RawSocket(queue, family, protocol, bindAddress);
			}
		};
	}
	
	private final Queue queue;
	
	private final ProtocolFamily family;
	private final int protocol;
	private final Address bindAddress;
	
	private final Executor loop = new SerialExecutor(RawSocket.class);

	private final Object lock = new Object();
	private NativeRawSocket socket = null;
	private boolean closed = false;

	private static final class ToWrite {
		public final Address address;
		public final ByteBuffer buffer;
		public final SendCallback callback;
		public ToWrite(Address address, ByteBuffer buffer, SendCallback callback) {
			this.address = address;
			this.buffer = buffer;
			this.callback = callback;
		}
	}

	private final Deque<ToWrite> toWriteQueue = new LinkedList<>();

	private RawSocket(Queue queue, ProtocolFamily family, int protocol, Address bindAddress) {
		this.queue = queue;
		this.family = family;
		this.protocol = protocol;
		this.bindAddress = bindAddress;
	}
	
	@Override
	public void connect(final Connection callback) {
		final NativeRawSocket s;
		try {
			s = new NativeRawSocket((family == StandardProtocolFamily.INET) ? NativeRawSocket.PF_INET : NativeRawSocket.PF_INET6, protocol);
		} catch (final Exception ee) {
			synchronized (lock) {
				closed = true;
				queue.execute(new Runnable() {
					@Override
					public void run() {
						callback.failed(new IOException("Failed to be created", ee));
					}
				});
				return;
			}
		}
		
		if (bindAddress != null) {
			try {
				InetAddress a = InetAddress.getByName(bindAddress.host); // Note this call blocks to resolve host (DNS resolution) //TODO Test with unresolved
				s.bind(a);
			} catch (final Exception ee) {
				try {
					s.close();
				} catch (Exception ce) {
				}
				synchronized (lock) {
					closed = true;
					queue.execute(new Runnable() {
						@Override
						public void run() {
							callback.failed( new IOException("Could not bind to: " + bindAddress, ee));
						}
					});
					return;
				}
			}
		}
		
		loop.execute(new Runnable() {
			@Override
			public void run() {
				queue.execute(new Runnable() {
					@Override
					public void run() {
						callback.connected(null);
					}
				});
				
				Deque<ToWrite> q;
				synchronized (lock) {
					socket = s;
					q = toWriteQueue;
				}

				for (final ToWrite toWrite : q) {
					try {
						while (toWrite.buffer.hasRemaining()) {
							int r = s.write(InetAddress.getByName(toWrite.address.host), toWrite.buffer.array(), toWrite.buffer.arrayOffset() + toWrite.buffer.position(), toWrite.buffer.remaining());
							if (r == 0) {
								throw new IOException("Error writing to: " + toWrite.address.host);
							}
							toWrite.buffer.position(toWrite.buffer.position() + r);
						}
	
						queue.execute(new Runnable() {
							@Override
							public void run() {
								toWrite.callback.sent();
							}
						});
					} catch (final Exception e) {
						queue.execute(new Runnable() {
							@Override
							public void run() {
								toWrite.callback.failed(new IOException("Could not write", e));
							}
						});
					}
				}
				q.clear();
				
				while (true) {
					byte[] recvData = new byte[84];
					byte[] srcAddress = new byte[(family == StandardProtocolFamily.INET) ? 4 : 16];
					try {
						int r = s.read(recvData, 0, recvData.length, srcAddress);
						final String host = InetAddress.getByAddress(srcAddress).getHostAddress();
						LOGGER.debug("Received raw packet: {} bytes from: {}", r, host);

						final ByteBuffer b = ByteBuffer.wrap(recvData, 0, r);
						if (family == StandardProtocolFamily.INET) {
							int headerLength = (b.get() & 0x0F) * 4;
							b.position(headerLength);
						}
						
						queue.execute(new Runnable() {
							@Override
							public void run() {
								callback.received(new Address(host, 0), b);
							}
						});
					} catch (Exception e) {
						LOGGER.trace("Error, probably closed", e);
						break;
					}
				}
				
				queue.execute(new Runnable() {
					@Override
					public void run() {
						callback.closed();
					}
				});
			}
		});
	}
	
	@Override
	public void send(Address address, ByteBuffer buffer, final SendCallback callback) {
		try {
			NativeRawSocket s;
			synchronized (lock) {
				if (closed) {
					throw new IOException("Closed");
				}
				if (socket == null) {
					toWriteQueue.addLast(new ToWrite(address, buffer, callback));
					return;
				}
				s = socket;
			}
			
			while (buffer.hasRemaining()) {
				int r = s.write(InetAddress.getByName(address.host), buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
				if (r == 0) {
					throw new IOException("Error writing to: " + address.host);
				}
				buffer.position(buffer.position() + r);
			}

			queue.execute(new Runnable() {
				@Override
				public void run() {
					callback.sent();
				}
			});
		} catch (final Exception e) {
			queue.execute(new Runnable() {
				@Override
				public void run() {
					callback.failed(new IOException("Could not write", e));
				}
			});
		}
	}
	
	@Override
	public void close() {
		try {
			NativeRawSocket s;
			synchronized (lock) {
				if (closed) {
					return;
				}
				closed = true;
				s = socket;
			}
			
			if (s != null) {
				s.close();
			}
		} catch (IOException e) {
			LOGGER.error("Could not close", e);
		}
	}
}
