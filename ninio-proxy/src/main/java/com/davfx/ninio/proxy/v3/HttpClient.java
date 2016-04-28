package com.davfx.ninio.proxy.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.ByteBufferAllocator;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.TcpSocket;
import com.davfx.ninio.core.v3.UdpSocket;
import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;

public final class HttpClient implements Disconnectable {
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpClient.class);
	
	public static interface Builder extends NinioBuilder<HttpClient> {
		Builder with(Executor executor);
		Builder with(TcpSocket.Builder connectorFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private Executor executor = null;
			private TcpSocket.Builder connectorFactory = TcpSocket.builder();
			
			@Override
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder with(TcpSocket.Builder connectorFactory) {
				this.connectorFactory = connectorFactory;
				return this;
			}

			@Override
			public HttpClient create(Queue queue) {
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				return new HttpClient(queue, executor, connectorFactory);
			}
		};
	}
	
	private final Queue queue;

	private final TcpSocket.Builder proxyConnectorFactory;
	private final Executor proxyExecutor;

	private Connector proxyConnector = null;
	private int nextConnectionId = 0;

	private static final class InnerConnection {
		public final Address address;
		public final Failing failing;
		public final Receiver receiver;
		public final Closing closing;
		public final Connecting connecting;
		
		public InnerConnection(Address address, Failing failing, Receiver receiver, Closing closing, Connecting connecting) {
			this.address = address;
			this.failing = failing;
			this.receiver = receiver;
			this.closing = closing;
			this.connecting = connecting;
		}
	}

	private final Map<Integer, InnerConnection> connections = new HashMap<>();

	private HttpClient(Queue queue, Executor proxyExecutor, TcpSocket.Builder proxyConnectorFactory) {
		this.queue = queue;
		this.proxyExecutor = proxyExecutor;
		this.proxyConnectorFactory = proxyConnectorFactory;
	}
	
	@Override
	public void close() {
		proxyExecutor.execute(new Runnable() {
			@Override
			public void run() {
				if (proxyConnector != null) {
					proxyConnector.close();
				}
				connections.clear();
			}
		});
	}

	public TcpSocket.Builder tcp() {
		return new TcpSocket.Builder() {
			private Address connectAddress = null;
			
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
			@Override
			public TcpSocket.Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public TcpSocket.Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public TcpSocket.Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public TcpSocket.Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}

			@Override
			public TcpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}

			@Override
			public TcpSocket.Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public Connector create(Queue ignoredQueue) {
				return createConnector(ProxyCommons.Types.TCP, null, connectAddress, failing, receiver, closing, connecting);
			}
		};
	}
	
	public UdpSocket.Builder udp() {
		return new UdpSocket.Builder() {
			private Address bindAddress = null;
			
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
			@Override
			public UdpSocket.Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public UdpSocket.Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public UdpSocket.Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public UdpSocket.Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public UdpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}
			
			@Override
			public UdpSocket.Builder bind(Address bindAddress) {
				this.bindAddress = bindAddress;
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				return createConnector(ProxyCommons.Types.UDP, null, bindAddress, failing, receiver, closing, connecting);
			}
		};
	}
	
	private Connector createConnector(final String type, final String header, Address connectAddress, Failing failing, Receiver receiver, Closing closing, Connecting connecting) {
		final InnerConnection innerConnection = new InnerConnection(connectAddress, failing, receiver, closing, connecting);
		return new Connector() {
			private int connectionId;
			
			@Override
			public Connector send(final Address sendAddress, final ByteBuffer sendBuffer) {
				final Connector connector = this;
				
				proxyExecutor.execute(new Runnable() {
					private void closedRegisteredConnections(final IOException ioe) {
						for (final InnerConnection c : connections.values()) {
							c.failing.failed(ioe);
						}
						connections.clear();
					}
					@Override
					public void run() {
						if (proxyConnector == null) {
							proxyConnector = proxyConnectorFactory
									.closing(new Closing() {
										@Override
										public void closed() {
											proxyConnector = null;
											closedRegisteredConnections(new IOException("Connection to proxy lost"));
										}
									})
									.failing(new Failing() {
										@Override
										public void failed(IOException e) {
											proxyConnector = null;
											closedRegisteredConnections(new IOException("Connection to proxy lost", e));
										}
									})
									.receiving(new Receiver() {
										private int readConnectionId = -1;
										private ByteBuffer readConnectionIdByteBuffer;
										private int readLength = -1;
										private ByteBuffer readLengthByteBuffer;
										private int readHostLength = -1;
										private ByteBuffer readHostLengthByteBuffer;
										private String readHost = null;
										private ByteBuffer readHostByteBuffer;
										private int readPort = -1;
										private ByteBuffer readPortByteBuffer;
										
										private int command = -1;
										
										private int readInt(ByteBuffer receivedBuffer, ByteBuffer to) {
											while (true) {
												if (!receivedBuffer.hasRemaining()) {
													return -1;
												}
												to.put(receivedBuffer.get());
												if (to.position() == to.capacity()) {
													to.flip();
													return to.getInt();
												}
											}
										}
										private String readString(ByteBuffer receivedBuffer, ByteBuffer to) {
											while (true) {
												if (!receivedBuffer.hasRemaining()) {
													return null;
												}
												to.put(receivedBuffer.get());
												if (to.position() == to.capacity()) {
													return new String(to.array(), to.arrayOffset(), to.position(), Charsets.UTF_8);
												}
											}
										}
										
										@Override
										public void received(Connector receivedConnector, Address receivedAddress, ByteBuffer receivedBuffer) {
											while (receivedBuffer.hasRemaining()) {
												if (readConnectionIdByteBuffer == null) {
													readConnectionIdByteBuffer = ByteBuffer.allocate(Ints.BYTES);
												}
												readConnectionId = readInt(receivedBuffer, readConnectionIdByteBuffer);
												
												if (readConnectionId >= 0) {
													readConnectionIdByteBuffer = null;
													
													if (command < 0) {
														if (!receivedBuffer.hasRemaining()) {
															return;
														}
														command = receivedBuffer.get() & 0xFF;
													}

													final InnerConnection receivedInnerConnection = connections.get(readConnectionId);

													switch (command) {
													case ProxyCommons.Commands.SEND_WITH_ADDRESS: {
														if (readHostLengthByteBuffer == null) {
															readHostLengthByteBuffer = ByteBuffer.allocate(Ints.BYTES);
														}
														readHostLength = readInt(receivedBuffer, readHostLengthByteBuffer);
														
														if (readHostLength >= 0) {
															readHostLengthByteBuffer = null;

															if (readHostByteBuffer == null) {
																readHostByteBuffer = ByteBuffer.allocate(readHostLength);
															}
															readHost = readString(receivedBuffer, readHostByteBuffer);
															
															if (readHost != null) {
																readHostByteBuffer = null;

																if (readPortByteBuffer == null) {
																	readPortByteBuffer = ByteBuffer.allocate(Ints.BYTES);
																}
																readPort = readInt(receivedBuffer, readPortByteBuffer);
																
																if (readPort > 0) {
																	readPortByteBuffer = null;
																	
																	if (readLengthByteBuffer == null) {
																		readLengthByteBuffer = ByteBuffer.allocate(Ints.BYTES);
																	}
																	readLength = readInt(receivedBuffer, readLengthByteBuffer);
																	
																	if (readLength >= 0) {
																		readLengthByteBuffer = null;
		
																		if (receivedInnerConnection != null) {
																			final ByteBuffer b = receivedBuffer.duplicate();
																			b.limit(Math.max(b.limit(), b.position() + readLength));
																			receivedInnerConnection.receiver.received(connector, new Address(readHost, readPort), b);
																		}
																	}
																}
															}
														}
														break;
													}
													case ProxyCommons.Commands.SEND_WITHOUT_ADDRESS: {
														if (readLengthByteBuffer == null) {
															readLengthByteBuffer = ByteBuffer.allocate(Ints.BYTES);
														}
														readLength = readInt(receivedBuffer, readLengthByteBuffer);
														
														if (readLength >= 0) {
															readLengthByteBuffer = null;

															if (receivedInnerConnection != null) {
																final ByteBuffer b = receivedBuffer.duplicate();
																b.limit(Math.max(b.limit(), b.position() + readLength));
																receivedInnerConnection.receiver.received(connector, null, b);
															}
														}
														break;
													}
													case ProxyCommons.Commands.CLOSE: {
														connections.remove(connectionId);
														if (receivedInnerConnection != null) {
															receivedInnerConnection.closing.closed();
														}
														break;
													}
													}
												}
											}
										}
									})
									.create(queue);
						}
						
						connectionId = nextConnectionId;
						nextConnectionId++;
						
						connections.put(connectionId, innerConnection);

						byte[] typeAsBytes = type.getBytes(Charsets.UTF_8);
						byte[] headerAsBytes = header.getBytes(Charsets.UTF_8);

						ByteBuffer b;
						if (innerConnection.address == null) {
							b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + typeAsBytes.length + Ints.BYTES + headerAsBytes.length);
							b.put((byte) ProxyCommons.Commands.CONNECT_WITHOUT_ADDRESS);
							b.putInt(connectionId);
							b.putInt(headerAsBytes.length);
							b.put(headerAsBytes);
							b.putInt(typeAsBytes.length);
							b.put(typeAsBytes);
							b.flip();
						} else {
							byte[] hostAsBytes = innerConnection.address.getHost().getBytes(Charsets.UTF_8);
							b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + typeAsBytes.length + Ints.BYTES + headerAsBytes.length + Ints.BYTES + hostAsBytes.length + Ints.BYTES);
							b.put((byte) ProxyCommons.Commands.CONNECT_WITH_ADDRESS);
							b.putInt(connectionId);
							b.putInt(headerAsBytes.length);
							b.put(headerAsBytes);
							b.putInt(typeAsBytes.length);
							b.put(typeAsBytes);
							b.putInt(hostAsBytes.length);
							b.put(hostAsBytes);
							b.putInt(innerConnection.address.getPort());
							b.flip();
						}
						proxyConnector.send(null, b);
						
						innerConnection.connecting.connected(connector);
					}
				});
				return this;
			}
			
			@Override
			public void close() {
				proxyExecutor.execute(new Runnable() {
					@Override
					public void run() {
						connections.remove(connectionId);

						if (proxyConnector == null) {
							return;
						}

						ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES);
						b.put((byte) ProxyCommons.Commands.CLOSE);
						b.putInt(connectionId);
						b.flip();

						proxyConnector.send(null, b);
					}
				});
			}
		};
	}
}
