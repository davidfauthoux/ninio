package com.davfx.ninio.proxy.v3;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.davfx.ninio.core.v3.Address;
import com.davfx.ninio.core.v3.ByteBufferAllocator;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.ExecutorUtils;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.RawSocket;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.TcpSocket;
import com.davfx.ninio.core.v3.UdpSocket;
import com.davfx.ninio.http.v3.HttpClient;
import com.davfx.ninio.http.v3.HttpSocket;
import com.davfx.ninio.http.v3.HttpSpecification;
import com.davfx.ninio.http.v3.WebsocketSocket;
import com.davfx.util.ClassThreadFactory;
import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;

public final class ProxyClient implements ProxyConnectorProvider {
	
	public static NinioBuilder<ProxyConnectorProvider> defaultClient(final Address address) {
		return new NinioBuilder<ProxyConnectorProvider>() {
			@Override
			public ProxyConnectorProvider create(Queue queue) {
				final ExecutorService executor = Executors.newSingleThreadExecutor(new ClassThreadFactory(ProxyServer.class, true));
				final ProxyClient client = ProxyClient.builder().with(executor).with(TcpSocket.builder().to(address)).create(queue);
				return new ProxyConnectorProvider() {
					@Override
					public void close() {
						client.close();
						ExecutorUtils.shutdown(executor);
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
		public final Closing closing;
		public final Connecting connecting;
		
		public InnerConnection(Failing failing, Receiver receiver, Closing closing, Connecting connecting) {
			this.failing = failing;
			this.receiver = receiver;
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
				connections.clear();
			}
		});
	}

	@Override
	public WithHeaderSocketBuilder factory() {
		return new WithHeaderSocketBuilder() {
			private String header;
			private Address address;
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
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
			public WithHeaderSocketBuilder header(String header) {
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
				return createConnector(header, address, failing, receiver, closing, connecting);
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
				return createConnector(ProxyCommons.Types.TCP, connectAddress, failing, receiver, closing, connecting);
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
				return createConnector(ProxyCommons.Types.SSL, connectAddress, failing, receiver, closing, connecting);
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
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				return createConnector(ProxyCommons.Types.UDP, null, failing, receiver, closing, connecting);
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
			public RawSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public Connector create(Queue queue) {
				return createConnector(ProxyCommons.Types.RAW + String.valueOf((family == StandardProtocolFamily.INET) ? '4' : '6') + String.valueOf(protocol), null, failing, receiver, closing, connecting);
			}
		};
	}
	
	@Override
	public WebsocketSocket.Builder websocket() {
		return new WebsocketSocket.Builder() {
			private Address connectAddress = null;
			
			private String path = String.valueOf(HttpSpecification.PATH_SEPARATOR);

			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
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
			public WebsocketSocket.Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public WebsocketSocket.Builder with(HttpClient httpClient) {
				return this;
			}
			
			@Override
			public WebsocketSocket.Builder route(String path) {
				this.path = path;
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
				return createConnector(ProxyCommons.Types.WEBSOCKET + path, connectAddress, failing, receiver, closing, connecting);
			}
		};
	}
	
	@Override
	public HttpSocket.Builder http() {
		return new HttpSocket.Builder() {
			private Address connectAddress = null;
			
			private String path = String.valueOf(HttpSpecification.PATH_SEPARATOR);

			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
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
			public HttpSocket.Builder to(Address connectAddress) {
				this.connectAddress = connectAddress;
				return this;
			}
			
			@Override
			public HttpSocket.Builder with(HttpClient httpClient) {
				return this;
			}
			
			@Override
			public HttpSocket.Builder route(String path) {
				this.path = path;
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
				return createConnector(ProxyCommons.Types.HTTP + path, connectAddress, failing, receiver, closing, connecting);
			}
		};
	}
	
	private Connector createConnector(final String header, final Address connectAddress, Failing failing, Receiver receiver, Closing closing, Connecting connecting) {
		final InnerConnection innerConnection = new InnerConnection(failing, receiver, closing, connecting);
		final Connector createdConnector = new Connector() {
			@Override
			public Connector send(final Address sendAddress, final ByteBuffer sendBuffer) {
				proxyExecutor.execute(new Runnable() {
					private void closedRegisteredConnections(IOException ioe) {
						for (InnerConnection c : connections.values()) {
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
										public void received(Address receivedAddress, ByteBuffer receivedBuffer) {
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
															receivedInnerConnection.receiver.received(new Address(readHost, readPort), b);
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
															receivedInnerConnection.receiver.received(null, b);
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
									})
									.create(queue);

							byte[] headerAsBytes = header.getBytes(Charsets.UTF_8);
	
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
						connections.remove(innerConnection.connectionId);

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
		};
		
		proxyExecutor.execute(new Runnable() {
			@Override
			public void run() {
				innerConnection.connectionId = nextConnectionId;
				nextConnectionId++;
				
				connections.put(innerConnection.connectionId, innerConnection);
				if (innerConnection.connecting != null) {
					innerConnection.connecting.connected();
				}
			}
		});

		return createdConnector;
	}
}
