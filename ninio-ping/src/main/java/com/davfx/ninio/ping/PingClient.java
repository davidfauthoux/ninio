package com.davfx.ninio.ping;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.NopConnecterConnectingCallback;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.RawSocket;

public final class PingClient implements PingConnecter {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PingClient.class);
	
	private static final int ICMP_PROTOCOL = 1;
	private static final long ID_LIMIT = 1L << 32;
	
	public static interface Builder extends NinioBuilder<PingConnecter> {
		Builder with(Executor executor);
		Builder with(RawSocket.Builder connectorFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private Executor executor = null;
			private RawSocket.Builder connectorFactory = RawSocket.builder();
			
			@Override
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder with(RawSocket.Builder connectorFactory) {
				this.connectorFactory = connectorFactory;
				return this;
			}

			@Override
			public PingClient create(Queue queue) {
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				return new PingClient(executor, connectorFactory.protocol(ICMP_PROTOCOL).create(queue));
			}
		};
	}
	
	private final Executor executor;
	private final Connecter connecter;
	private long nextId = 0L;
	
	//TODO gerer les multiples appels au callback (qd closed)
	
	public PingClient(Executor executor, Connecter connecter) {
		this.executor = executor;
		this.connecter = connecter;
	}
	
	private static void closeSendCallbacks(Map<Long, PingConnecter.Connecting.Callback> receivers) {
		IOException e = new IOException("Closed");
		for (PingConnecter.Connecting.Callback c : receivers.values()) {
			c.failed(e);
		}
		receivers.clear();
	}
	
	@Override
	public PingConnecter.Connecting connect(final PingConnecter.Callback callback) {
		final Map<Long, PingConnecter.Connecting.Callback> receivers = new HashMap<>();
		
		final Connecter.Connecting connecting = connecter.connect(new Connecter.Callback() {
			@Override
			public void received(Address address, final ByteBuffer buffer) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						long now = System.nanoTime();
						long time;
						long id;
						try {
							buffer.get(); // type
							buffer.get(); // code
							buffer.getShort(); // checksum
							short identifier = buffer.getShort(); // identifier
							short sequence = buffer.getShort(); // sequence
							time = buffer.getLong();
							id = ((identifier & 0xFFFF) << 16) | (sequence & 0xFFFF);
						} catch (Exception e) {
							LOGGER.error("Invalid packet", e);
							return;
						}

						double delta = (now - time) / 1_000_000_000d;
						
						PingConnecter.Connecting.Callback r = receivers.remove(id);
						if (r == null) {
							return;
						}
						r.received(delta);
					}
				});
			}
			
			@Override
			public void failed(IOException ioe) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						closeSendCallbacks(receivers);
					}
				});
				callback.failed(ioe);
			}
			
			@Override
			public void connected(Address address) {
				callback.connected(address);
			}
			
			@Override
			public void closed() {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						closeSendCallbacks(receivers);
					}
				});
				callback.closed();
			}
		});

		return new Connecting() {
			private long id = -1L;
			
			@Override
			public Cancelable ping(final String host, final Callback callback) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (id < 0L) {
							id = nextId;
							nextId++;
							if (nextId == ID_LIMIT) {
								nextId = 0L;
							}
							receivers.put(id, callback);
						}

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
						
						connecting.send(new Address(host, 0), b, new NopConnecterConnectingCallback());
					}
				});
				
				return new Cancelable() {
					@Override
					public void cancel() {
						executor.execute(new Runnable() {
							@Override
							public void run() {
								if (id < 0L) {
									return;
								}
								receivers.remove(id);
							}
						});
					}
				};
			}
			
			@Override
			public void close() {
				connecting.close();

				executor.execute(new Runnable() {
					@Override
					public void run() {
						closeSendCallbacks(receivers);
					}
				});
			}
		};
	}
}
