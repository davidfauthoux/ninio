package com.davfx.ninio.proxy;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Buffering;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.RawSocket;
import com.davfx.ninio.core.Receiver;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.core.TcpdumpSocket;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpSocket;
import com.davfx.ninio.http.HttpSpecification;
import com.davfx.ninio.http.WebsocketSocket;
import com.davfx.ninio.util.SerialExecutor;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;

public final class ProxyClient implements ProxyConnectorProvider {
	
	public static NinioBuilder<ProxyConnectorProvider> defaultClient(final Address address) {
		return new NinioBuilder<ProxyConnectorProvider>() {
			@Override
			public ProxyConnectorProvider create(Queue queue) {
				final Executor executor = new SerialExecutor(ProxyClient.class);
				final ProxyClient client = ProxyClient.builder().with(executor).with(TcpSocket.builder().to(address)).create(queue);
				return new ProxyConnectorProvider() {
					@Override
					public void close() {
						client.close();
					}
					
					@Override
					public WithHeaderSocketBuilder factory() {
						return client.factory();
					}
					@Override
					public TcpSocket.Builder tcp() {
						return client.tcp();
					}
					@Override
					public UdpSocket.Builder udp() {
						return client.udp();
					}
					@Override
					public TcpdumpSocket.Builder tcpdump() {
						return client.tcpdump();
					}
					@Override
					public RawSocket.Builder raw() {
						return client.raw();
					}
					@Override
					public TcpSocket.Builder ssl() {
						return client.ssl();
					}
					@Override
					public WebsocketSocket.Builder websocket() {
						return client.websocket();
					}
					@Override
					public HttpSocket.Builder http() {
						return client.http();
					}
				};
			}
		};
	}
	
	public static interface Builder extends NinioBuilder<ProxyClient> {
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
			public ProxyClient create(Queue queue) {
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				return new ProxyClient(queue, executor, connectorFactory);
			}
		};
	}
	
	private final Queue queue;

	private final TcpSocket.Builder proxyConnectorFactory;
	private final Executor proxyExecutor;

	private Connector proxyConnector = null;
	private int nextConnectionId = 0;

	private static final class InnerConnection {
		public int connectionId;
		public final Failing failing;
		public final Receiver receiver;
		@SuppressWarnings("unused") // Unused for now
		public final Buffering buffering;
		public final Closing closing;
		public final Connecting connecting;
		
		public InnerConnection(Failing failing, Receiver receiver, Buffering buffering, Closing closing, Connecting connecting) {
			this.failing = failing;
			this.receiver = receiver;
			this.buffering = buffering;
			this.closing = closing;
			this.connecting = connecting;
		}
	}

	private final Map<Integer, InnerConnection> connections = new HashMap<>();

	private ProxyClient(Queue queue, Executor proxyExecutor, TcpSocket.Builder proxyConnectorFactory) {
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
				// connections.clear();
			}
		});
		//%% ExecutorUtils.waitFor(proxyExecutor);
	}

	@Override
	public WithHeaderSocketBuilder factory() {
		return new WithHeaderSocketBuilder() {
			private Header header;
			private Address address;
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			private Buffering buffering = null;

			@Override
			public WithHeaderSocketBuilder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public WithHeaderSocketBuilder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public WithHeaderSocketBuilder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public WithHeaderSocketBuilder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public WithHeaderSocketBuilder buffering(Buffering buffering) {
				this.buffering = buffering;
				return this;
			}
			
			@Override
			public WithHeaderSocketBuilder header(Header header) {
				this.header = header;
				return this;
			}
			
			@Override
			public WithHeaderSocketBuilder with(Address address) {
				this.address = address;
				return this;
			}

			@Override
			public Connector create(Queue ignoredQueue) {
				if (header == null) {
					throw new NullPointerException("header");
				}
				return createConnector(header, address, failing, receiver, buffering, closing, connecting);
			}
		};
	}

	@Override
	public TcpSocket.Builder tcp() {
		return new TcpSocket.Builder() {
			private Address connectAddress = null;
			
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			private Buffering buffering = null;
			
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
			public TcpSocket.Builder buffering(Buffering buffering) {
				this.buffering = buffering;
				return this;
			}
			
			@Override
			public TcpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}

			@Override
			public TcpSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public TcpSocket.Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public Connector create(Queue ignoredQueue) {
				return createConnector(new Header(ProxyCommons.Types.TCP), connectAddress, failing, receiver, buffering, closing, connecting);
			}
		};
	}
	
	@Override
	public TcpSocket.Builder ssl() {
		return new TcpSocket.Builder() {
			private Address connectAddress = null;
			
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			private Buffering buffering = null;
			
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
			public TcpSocket.Builder buffering(Buffering buffering) {
				this.buffering = buffering;
				return this;
			}
			
			@Override
			public TcpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}

			@Override
			public TcpSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public TcpSocket.Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public Connector create(Queue ignoredQueue) {
				return createConnector(new Header(ProxyCommons.Types.SSL), connectAddress, failing, receiver, buffering, closing, connecting);
			}
		};
	}
	
	@Override
	public UdpSocket.Builder udp() {
		return new UdpSocket.Builder() {
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			private Buffering buffering = null;
			
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
			public UdpSocket.Builder buffering(Buffering buffering) {
				this.buffering = buffering;
				return this;
			}
			
			@Override
			public UdpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}
			
			@Override
			public UdpSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				return createConnector(new Header(ProxyCommons.Types.UDP), null, failing, receiver, buffering, closing, connecting);
			}
		};
	}
	
	@Override
	public TcpdumpSocket.Builder tcpdump() {
		return new TcpdumpSocket.Builder() {
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			private String interfaceId = null;
			private String rule = null;
			private Buffering buffering = null;

			@Override
			public TcpdumpSocket.Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public TcpdumpSocket.Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public TcpdumpSocket.Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public TcpdumpSocket.Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public TcpdumpSocket.Builder buffering(Buffering buffering) {
				this.buffering = buffering;
				return this;
			}
			
			@Override
			public TcpdumpSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public TcpdumpSocket.Builder on(String interfaceId) {
				this.interfaceId = interfaceId;
				return this;
			}
			@Override
			public TcpdumpSocket.Builder rule(String rule) {
				this.rule = rule;
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				return createConnector(new Header(ProxyCommons.Types.TCPDUMP, ImmutableMap.of("interfaceId", interfaceId, "rule", rule)), null, failing, receiver, buffering, closing, connecting);
			}
		};
	}
	
	@Override
	public RawSocket.Builder raw() {
		return new RawSocket.Builder() {
			private ProtocolFamily family = StandardProtocolFamily.INET;
			private int protocol = 0;

			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			private Buffering buffering = null;

			@Override
			public RawSocket.Builder family(ProtocolFamily family) {
				this.family = family;
				return this;
			}
			@Override
			public RawSocket.Builder protocol(int protocol) {
				this.protocol = protocol;
				return this;
			}
			
			@Override
			public RawSocket.Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public RawSocket.Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public RawSocket.Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public RawSocket.Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public RawSocket.Builder buffering(Buffering buffering) {
				this.buffering = buffering;
				return this;
			}
			
			@Override
			public RawSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				return createConnector(new Header(ProxyCommons.Types.RAW, ImmutableMap.of("family", (family == StandardProtocolFamily.INET6) ? "6" : "4", "protocol", String.valueOf(protocol))), null, failing, receiver, buffering, closing, connecting);
			}
		};
	}
	
	@Override
	public WebsocketSocket.Builder websocket() {
		return new WebsocketSocket.Builder() {
			private Address connectAddress = null;
			
			private String route = String.valueOf(HttpSpecification.PATH_SEPARATOR);

			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			private Buffering buffering = null;

			@Override
			public WebsocketSocket.Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public WebsocketSocket.Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public WebsocketSocket.Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public WebsocketSocket.Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public WebsocketSocket.Builder buffering(Buffering buffering) {
				this.buffering = buffering;
				return this;
			}
			
			@Override
			public WebsocketSocket.Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public WebsocketSocket.Builder with(HttpClient httpClient) {
				return this;
			}
			
			@Override
			public WebsocketSocket.Builder route(String route) {
				this.route = route;
				return this;
			}
			
			@Override
			public TcpSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public TcpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				return createConnector(new Header(ProxyCommons.Types.WEBSOCKET, ImmutableMap.of("route", route)), connectAddress, failing, receiver, buffering, closing, connecting);
			}
		};
	}
	
	@Override
	public HttpSocket.Builder http() {
		return new HttpSocket.Builder() {
			private Address connectAddress = null;
			
			private String route = String.valueOf(HttpSpecification.PATH_SEPARATOR);

			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			private Buffering buffering = null;

			@Override
			public HttpSocket.Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public HttpSocket.Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public HttpSocket.Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public HttpSocket.Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public HttpSocket.Builder buffering(Buffering buffering) {
				this.buffering = buffering;
				return this;
			}
			
			@Override
			public HttpSocket.Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public HttpSocket.Builder with(HttpClient httpClient) {
				return this;
			}
			
			@Override
			public HttpSocket.Builder route(String route) {
				this.route = route;
				return this;
			}
			
			@Override
			public TcpSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public TcpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				return createConnector(new Header(ProxyCommons.Types.HTTP, ImmutableMap.of("route", route)), connectAddress, failing, receiver, buffering, closing, connecting);
			}
		};
	}
	
	private Connector createConnector(Header header, Address connectAddress, Failing failing, Receiver receiver, Buffering buffering, Closing closing, Connecting connecting) {
		return new InnerConnector(header, connectAddress, failing, receiver, buffering, closing, connecting);
	}
	
	private final class InnerConnector implements Connector {
		private final Header header;
		private final Address connectAddress;
		private final InnerConnection innerConnection;
		
		public InnerConnector(Header header, final Address connectAddress, Failing failing, Receiver receiver, Buffering buffering, Closing closing, Connecting connecting) {
			this.header = header;
			this.connectAddress = connectAddress;
			
			innerConnection = new InnerConnection(failing, receiver, buffering, closing, connecting);

			proxyExecutor.execute(new Runnable() {
				@Override
				public void run() {
					innerConnection.connectionId = nextConnectionId;
					nextConnectionId++;
					
					connections.put(innerConnection.connectionId, innerConnection);
					if (innerConnection.connecting != null) {
						innerConnection.connecting.connected(InnerConnector.this, connectAddress);
					}
				}
			});
		}
		
		@Override
		public Connector send(final Address sendAddress, final ByteBuffer sendBuffer) {
			proxyExecutor.execute(new Runnable() {
				@Override
				public void run() {
					if (proxyConnector == null) {
						proxyConnector = proxyConnectorFactory
								.closing(new Closing() {
									@Override
									public void closed() {
										proxyExecutor.execute(new Runnable() {
											@Override
											public void run() {
												proxyConnector = null;
												for (InnerConnection c : connections.values()) {
													if (c.closing != null) {
														c.closing.closed();
													}
												}
												connections.clear();
											}
										});
									}
								})
								.failing(new Failing() {
									@Override
									public void failed(final IOException e) {
										proxyExecutor.execute(new Runnable() {
											@Override
											public void run() {
												proxyConnector = null;
												for (InnerConnection c : connections.values()) {
													if (c.failing != null) {
														c.failing.failed(e);
													}
												}
												connections.clear();
											}
										});
									}
								})
								.receiving(new Receiver() {
									private ByteBuffer readByteBuffer;

									private int readConnectionId = -1;
									private int command = -1;

									private int readHostLength = -1;
									private String readHost = null;
									private int readPort = -1;
									private int readLength = -1;
									
									private int readByte(int old, ByteBuffer receivedBuffer) {
										if (old >= 0) {
											return old;
										}
										if (!receivedBuffer.hasRemaining()) {
											return -1;
										}
										return receivedBuffer.get() & 0xFF;
									}
									private int readInt(int old, ByteBuffer receivedBuffer) {
										if (old >= 0) {
											return old;
										}
										if (readByteBuffer == null) {
											readByteBuffer = ByteBuffer.allocate(Ints.BYTES);
										}
										while (true) {
											if (!receivedBuffer.hasRemaining()) {
												return -1;
											}
											readByteBuffer.put(receivedBuffer.get());
											if (readByteBuffer.position() == readByteBuffer.capacity()) {
												readByteBuffer.flip();
												int i = readByteBuffer.getInt();
												readByteBuffer = null;
												return i;
											}
										}
									}
									private String readString(String old, ByteBuffer receivedBuffer, int len) {
										if (old != null) {
											return old;
										}
										if (readByteBuffer == null) {
											readByteBuffer = ByteBuffer.allocate(len);
										}
										while (true) {
											if (!receivedBuffer.hasRemaining()) {
												return null;
											}
											readByteBuffer.put(receivedBuffer.get());
											if (readByteBuffer.position() == readByteBuffer.capacity()) {
												String s = new String(readByteBuffer.array(), readByteBuffer.arrayOffset(), readByteBuffer.position(), Charsets.UTF_8);
												readByteBuffer = null;
												return s;
											}
										}
									}
									
									@Override
									public void received(Connector conn, Address receivedAddress, final ByteBuffer receivedBuffer) {
										proxyExecutor.execute(new Runnable() {
											@Override
											public void run() {
												while (true) {
													command = readByte(command, receivedBuffer);
													if (command < 0) {
														return;
													}
		
													readConnectionId = readInt(readConnectionId, receivedBuffer);
													if (readConnectionId < 0) {
														return;
													}
		
													switch (command) {
													case ProxyCommons.Commands.SEND_WITH_ADDRESS: {
														readHostLength = readInt(readHostLength, receivedBuffer);
														if (readHostLength < 0) {
															return;
														}
														readHost = readString(readHost, receivedBuffer, readHostLength);
														if (readHost == null) {
															return;
														}
														readPort = readInt(readPort, receivedBuffer);
														if (readPort < 0) {
															return;
														}
														readLength = readInt(readLength, receivedBuffer);
														if (readLength < 0) {
															return;
														}
														int len = readLength;
														if (receivedBuffer.remaining() < len) {
															len = receivedBuffer.remaining();
														}
														InnerConnection receivedInnerConnection = connections.get(readConnectionId);
														if (receivedInnerConnection != null) {
															ByteBuffer b = receivedBuffer.duplicate();
															b.limit(b.position() + len);
															if (receivedInnerConnection.receiver != null) {
																receivedInnerConnection.receiver.received(InnerConnector.this, new Address(readHost, readPort), b);
															}
														}
														receivedBuffer.position(receivedBuffer.position() + len);
														readLength -= len;
														if (readLength == 0) {
															readConnectionId = -1;
															command = -1;
															readHostLength = -1;
															readHost = null;
															readPort = -1;
															readLength = -1;
														}
														break;
													}
													case ProxyCommons.Commands.SEND_WITHOUT_ADDRESS: {
														readLength = readInt(readLength, receivedBuffer);
														if (readLength < 0) {
															return;
														}
														int len = readLength;
														if (receivedBuffer.remaining() < len) {
															len = receivedBuffer.remaining();
														}
														InnerConnection receivedInnerConnection = connections.get(readConnectionId);
														if (receivedInnerConnection != null) {
															ByteBuffer b = receivedBuffer.duplicate();
															b.limit(b.position() + len);
															if (receivedInnerConnection.receiver != null) {
																receivedInnerConnection.receiver.received(InnerConnector.this, null, b);
															}
														}
														receivedBuffer.position(receivedBuffer.position() + len);
														readLength -= len;
														if (readLength == 0) {
															readConnectionId = -1;
															command = -1;
															readLength = -1;
														}
														break;
													}
													case ProxyCommons.Commands.CLOSE: {
														InnerConnection receivedInnerConnection = connections.remove(readConnectionId);
														readConnectionId = -1;
														command = -1;
														if (receivedInnerConnection != null) {
															if (receivedInnerConnection.closing != null) {
																receivedInnerConnection.closing.closed();
															}
														}
														break;
													}
													}
												}
											}
										});
									}
								})
								.create(queue);

						byte[] headerAsBytes = header.toString().getBytes(Charsets.UTF_8);

						if (connectAddress == null) {
							ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + headerAsBytes.length);
							b.put((byte) ProxyCommons.Commands.CONNECT_WITHOUT_ADDRESS);
							b.putInt(innerConnection.connectionId);
							b.putInt(headerAsBytes.length);
							b.put(headerAsBytes);
							b.flip();
							proxyConnector.send(null, b);
						} else {
							byte[] hostAsBytes = connectAddress.host.getBytes(Charsets.UTF_8);
							ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + hostAsBytes.length + Ints.BYTES + Ints.BYTES + headerAsBytes.length);
							b.put((byte) ProxyCommons.Commands.CONNECT_WITH_ADDRESS);
							b.putInt(innerConnection.connectionId);
							b.putInt(hostAsBytes.length);
							b.put(hostAsBytes);
							b.putInt(connectAddress.port);
							b.putInt(headerAsBytes.length);
							b.put(headerAsBytes);
							b.flip();
							proxyConnector.send(null, b);
						}
					}

					if (sendAddress == null) {
						ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + sendBuffer.remaining());
						b.put((byte) ProxyCommons.Commands.SEND_WITHOUT_ADDRESS);
						b.putInt(innerConnection.connectionId);
						b.putInt(sendBuffer.remaining());
						b.put(sendBuffer);
						b.flip();
						proxyConnector.send(null, b);
					} else {
						byte[] hostAsBytes = sendAddress.host.getBytes(Charsets.UTF_8);
						ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + hostAsBytes.length + Ints.BYTES + Ints.BYTES + sendBuffer.remaining());
						b.put((byte) ProxyCommons.Commands.SEND_WITH_ADDRESS);
						b.putInt(innerConnection.connectionId);
						b.putInt(hostAsBytes.length);
						b.put(hostAsBytes);
						b.putInt(sendAddress.port);
						b.putInt(sendBuffer.remaining());
						b.put(sendBuffer);
						b.flip();
						proxyConnector.send(null, b);
					}
				}
			});
			return this;
		}
		
		@Override
		public void close() {
			proxyExecutor.execute(new Runnable() {
				@Override
				public void run() {
					// connections.remove(innerConnection.connectionId); // Will be removed when server closes connection

					if (proxyConnector == null) {
						return;
					}

					ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES);
					b.put((byte) ProxyCommons.Commands.CLOSE);
					b.putInt(innerConnection.connectionId);
					b.flip();

					proxyConnector.send(null, b);
				}
			});
		}
	}
}
