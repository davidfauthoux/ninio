package com.davfx.ninio.proxy;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferAllocator;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.RawSocket;
import com.davfx.ninio.core.SendCallback;
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

public final class ProxyClient implements ProxyProvider {
	
	public static NinioBuilder<ProxyProvider> defaultClient(final Address address) {
		return new NinioBuilder<ProxyProvider>() {
			@Override
			public ProxyProvider create(Queue queue) {
				final Executor executor = new SerialExecutor(ProxyClient.class);
				final ProxyClient client = ProxyClient.builder().with(executor).with(TcpSocket.builder().to(address)).create(queue);
				return new ProxyProvider() {
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

	private Connecter proxyConnector = null;
	private int nextConnectionId = 0;

	private static final class InnerConnection {
		public int connectionId;
		public Connection connection = null;
		
		public InnerConnection() {
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
			private ProxyHeader header;
			private Address address;
			
			@Override
			public WithHeaderSocketBuilder header(ProxyHeader header) {
				this.header = header;
				return this;
			}
			
			@Override
			public WithHeaderSocketBuilder with(Address address) {
				this.address = address;
				return this;
			}

			@Override
			public Connecter create(Queue ignoredQueue) {
				if (header == null) {
					throw new NullPointerException("header");
				}
				return createConnector(header, address);
			}
		};
	}

	@Override
	public TcpSocket.Builder tcp() {
		return new TcpSocket.Builder() {
			private Address connectAddress = null;
			
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
			public Connecter create(Queue ignoredQueue) {
				return createConnector(new ProxyHeader(ProxyCommons.Types.TCP), connectAddress);
			}
		};
	}
	
	@Override
	public TcpSocket.Builder ssl() {
		return new TcpSocket.Builder() {
			private Address connectAddress = null;
			
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
			public Connecter create(Queue ignoredQueue) {
				return createConnector(new ProxyHeader(ProxyCommons.Types.SSL), connectAddress);
			}
		};
	}
	
	@Override
	public UdpSocket.Builder udp() {
		return new UdpSocket.Builder() {
			@Override
			public UdpSocket.Builder with(ByteBufferAllocator byteBufferAllocator) {
				return this;
			}
			
			@Override
			public UdpSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public Connecter create(Queue queue) {
				return createConnector(new ProxyHeader(ProxyCommons.Types.UDP), null);
			}
		};
	}
	
	@Override
	public TcpdumpSocket.Builder tcpdump() {
		return new TcpdumpSocket.Builder() {
			private String interfaceId = null;
			private String rule = null;
			
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
			public Connecter create(Queue queue) {
				return createConnector(new ProxyHeader(ProxyCommons.Types.TCPDUMP, ImmutableMap.of("interfaceId", interfaceId, "rule", rule)), null);
			}
		};
	}
	
	@Override
	public RawSocket.Builder raw() {
		return new RawSocket.Builder() {
			private ProtocolFamily family = StandardProtocolFamily.INET;
			private int protocol = 0;

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
			public RawSocket.Builder bind(Address bindAddress) {
				return this;
			}
			
			@Override
			public Connecter create(Queue queue) {
				return createConnector(new ProxyHeader(ProxyCommons.Types.RAW, ImmutableMap.of("family", (family == StandardProtocolFamily.INET6) ? "6" : "4", "protocol", String.valueOf(protocol))), null);
			}
		};
	}
	
	@Override
	public WebsocketSocket.Builder websocket() {
		return new WebsocketSocket.Builder() {
			private Address connectAddress = null;
			
			private String route = String.valueOf(HttpSpecification.PATH_SEPARATOR);

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
			public Connecter create(Queue queue) {
				return createConnector(new ProxyHeader(ProxyCommons.Types.WEBSOCKET, ImmutableMap.of("route", route)), connectAddress);
			}
		};
	}
	
	@Override
	public HttpSocket.Builder http() {
		return new HttpSocket.Builder() {
			private Address connectAddress = null;
			
			private String route = String.valueOf(HttpSpecification.PATH_SEPARATOR);

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
			public Connecter create(Queue queue) {
				return createConnector(new ProxyHeader(ProxyCommons.Types.HTTP, ImmutableMap.of("route", route)), connectAddress);
			}
		};
	}
	
	private Connecter createConnector(ProxyHeader header, Address connectAddress) {
		return new InnerConnector(header, connectAddress);
	}
	
	private final class InnerConnector implements Connecter {
		private final ProxyHeader header;
		private final Address connectAddress;
		private final InnerConnection innerConnection;
		
		public InnerConnector(ProxyHeader header, final Address connectAddress) {
			this.header = header;
			this.connectAddress = connectAddress;
			
			innerConnection = new InnerConnection();

			proxyExecutor.execute(new Runnable() {
				@Override
				public void run() {
					innerConnection.connectionId = nextConnectionId;
					nextConnectionId++;
					
					connections.put(innerConnection.connectionId, innerConnection);
				}
			});
		}
		
		@Override
		public void connect(final Connection callback) {
			proxyExecutor.execute(new Runnable() {
				@Override
				public void run() {
					if (innerConnection.connection != null) {
						throw new IllegalStateException("connect() cannot be called twice");
					}
					innerConnection.connection = callback;

					reconnect();

					callback.connected(null);
				}
			});
		}
		
		private void reconnect() {
			if (proxyConnector == null) {
				proxyConnector = proxyConnectorFactory.create(queue);
				proxyConnector.connect(new Connection() {
					@Override
					public void connected(Address address) {
					}
					
					@Override
					public void closed() {
						proxyExecutor.execute(new Runnable() {
							@Override
							public void run() {
								proxyConnector = null;
								for (InnerConnection c : connections.values()) {
									c.connection.closed();
								}
								connections.clear();
							}
						});
					}

					@Override
					public void failed(final IOException e) {
						proxyExecutor.execute(new Runnable() {
							@Override
							public void run() {
								proxyConnector = null;
								for (InnerConnection c : connections.values()) {
									c.connection.failed(e);
								}
								connections.clear();
							}
						});
					}

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
					public void received(Address receivedAddress, final ByteBuffer receivedBuffer) {
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
											receivedInnerConnection.connection.received(new Address(readHost, readPort), b);
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
											receivedInnerConnection.connection.received(null, b);
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
											receivedInnerConnection.connection.closed();
										}
										break;
									}
									}
								}
							}
						});
					}
				});

				byte[] headerAsBytes = header.toString().getBytes(Charsets.UTF_8);

				SendCallback sendCallback = new SendCallback() {
					@Override
					public void failed(IOException e) {
						proxyConnector.close();
					}
					@Override
					public void sent() {
					}
				};
				
				if (connectAddress == null) {
					ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + headerAsBytes.length);
					b.put((byte) ProxyCommons.Commands.CONNECT_WITHOUT_ADDRESS);
					b.putInt(innerConnection.connectionId);
					b.putInt(headerAsBytes.length);
					b.put(headerAsBytes);
					b.flip();
					proxyConnector.send(null, b, sendCallback);
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
					proxyConnector.send(null, b, sendCallback);
				}
			}
		}
		
		@Override
		public void send(final Address sendAddress, final ByteBuffer sendBuffer, final SendCallback callback) {
			proxyExecutor.execute(new Runnable() {
				@Override
				public void run() {
					if (innerConnection.connection == null) {
						throw new IllegalStateException("send() must be called after connect()");
					}
					
					reconnect();

					if (sendAddress == null) {
						ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + sendBuffer.remaining());
						b.put((byte) ProxyCommons.Commands.SEND_WITHOUT_ADDRESS);
						b.putInt(innerConnection.connectionId);
						b.putInt(sendBuffer.remaining());
						b.put(sendBuffer);
						b.flip();
						proxyConnector.send(null, b, callback);
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
						proxyConnector.send(null, b, callback);
					}
				}
			});
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

					SendCallback sendCallback = new SendCallback() {
						@Override
						public void failed(IOException e) {
							proxyConnector.close();
						}
						@Override
						public void sent() {
						}
					};
					
					proxyConnector.send(null, b, sendCallback);
				}
			});
		}
	}
}
