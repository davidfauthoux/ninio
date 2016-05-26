package com.davfx.ninio.ping.v3;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.RawSocket;
import com.davfx.ninio.core.v3.Receiver;

public final class PingClient implements Disconnectable, AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PingClient.class);
	
	private static final int ICMP_PROTOCOL = 1;
	private static final long ID_LIMIT = 1L << 32;
	
	public static interface Builder extends NinioBuilder<PingClient> {
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
				return new PingClient(queue, executor, connectorFactory);
			}
		};
	}
	
	private final Executor executor;
	private final Connector connector;
	private final Map<Long, PingReceiver> receivers = new HashMap<>();
	private long nextId = 0L;

	public PingClient(Queue queue, final Executor executor, RawSocket.Builder connectorFactory) {
		this.executor = executor;
		connector = connectorFactory.receiving(new Receiver() {
			@Override
			public void received(Connector connector, Address address, final ByteBuffer buffer) {
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
						
						PingReceiver r = receivers.remove(id);
						if (r == null) {
							return;
						}
						r.received(delta);
					}
				});
			}
		}).protocol(ICMP_PROTOCOL).create(queue);
	}
	
	@Override
	public void close() {
		connector.close();
	}

	public PingRequestBuilder request() {
		return new PingRequestBuilder() {
			private PingReceiver receiver = null;

			@Override
			public PingRequestBuilder receiving(PingReceiver receiver) {
				this.receiver = receiver;
				return this;
			}

			private long id = -1L;
			
			@Override
			public PingRequest ping(final String host) {
				final PingReceiver r = receiver;
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (id < 0L) {
							id = nextId;
							nextId++;
							if (nextId == ID_LIMIT) {
								nextId = 0L;
							}
							receivers.put(id, r);
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
						
						connector.send(new Address(host, 0), b);
					}
				});
				
				return new PingRequest() {
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
		};
	}
}
