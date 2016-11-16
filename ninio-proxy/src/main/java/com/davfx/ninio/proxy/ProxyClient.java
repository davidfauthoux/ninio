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
import com.davfx.ninio.core.NinioProvider;
import com.davfx.ninio.core.RawSocket;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.core.TcpdumpMode;
import com.davfx.ninio.core.TcpdumpSocket;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.http.HttpConnecter;
import com.davfx.ninio.http.HttpSocket;
import com.davfx.ninio.http.HttpSpecification;
import com.davfx.ninio.http.WebsocketSocket;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Ints;

public final class ProxyClient implements ProxyProvider {
	
	public static NinioBuilder<ProxyProvider> defaultClient(final Address address) {
		return new NinioBuilder<ProxyProvider>() {
			@Override
			public ProxyProvider create(NinioProvider ninioProvider) {
				final ProxyClient client = ProxyClient.builder().with(TcpSocket.builder().to(address)).create(ninioProvider);
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
		@Deprecated
		Builder with(Executor executor);

		Builder with(TcpSocket.Builder connectorFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private TcpSocket.Builder connectorFactory = TcpSocket.builder();
			
			@Deprecated
			@Override
			public Builder with(Executor executor) {
				return this;
			}
			
			@Override
			public Builder with(TcpSocket.Builder connectorFactory) {
				this.connectorFactory = connectorFactory;
				return this;
			}

			@Override
			public ProxyClient create(NinioProvider ninioProvider) {
				return new ProxyClient(ninioProvider, connectorFactory);
			}
		};
	}
	
	private final Executor proxyExecutor;
	private final Connecter proxyConnector;
	private int nextConnectionId = 0;

	private static final class InnerConnection {
		public int connectionId;
		public Connection connection = null;
		
		public InnerConnection() {
		}
	}

	private final Map<Integer, InnerConnection> connections = new HashMap<>();

	private ProxyClient(NinioProvider ninioProvider, final TcpSocket.Builder proxyConnectorFactory) {
		proxyExecutor = ninioProvider.executor();
		
		proxyConnector = proxyConnectorFactory.create(ninioProvider);
		proxyConnector.connect(new Connection() {
			@Override
			public void connected(Address address) {
			}
			
			@Override
			public void closed() {
				proxyExecutor.execute(new Runnable() {
					@Override
					public void run() {
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

			private int readIpLength = -1;
			private byte[] readIp = null;
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
			private byte[] readBytes(ByteBuffer receivedBuffer, int len) {
				if (readByteBuffer == null) {
					readByteBuffer = ByteBuffer.allocate(len);
				}
				int l = len - readByteBuffer.position();
				if (l > receivedBuffer.remaining()) {
					l = receivedBuffer.remaining();
				}
				System.arraycopy(receivedBuffer.array(), receivedBuffer.arrayOffset() + receivedBuffer.position(), readByteBuffer.array(), readByteBuffer.arrayOffset() + readByteBuffer.position(), l);
				receivedBuffer.position(receivedBuffer.position() + l);
				readByteBuffer.position(readByteBuffer.position() + l);
				if (readByteBuffer.position() == readByteBuffer.capacity()) {
					byte[] b = readByteBuffer.array();
					readByteBuffer = null;
					return b;
				}
				return null;
			}
			private int readInt(int old, ByteBuffer receivedBuffer) {
				if (old >= 0) {
					return old;
				}
				byte[] r = readBytes(receivedBuffer, Ints.BYTES);
				if (r == null) {
					return -1;
				}
				return ByteBuffer.wrap(r).getInt();
			}
			/*
			private String readString(String old, ByteBuffer receivedBuffer, int len) {
				if (old != null) {
					return old;
				}
				byte[] r = readBytes(receivedBuffer, len);
				if (r == null) {
					return null;
				}
				return new String(r, 0, r.length, Charsets.UTF_8);
			}
			*/
			private byte[] readBytes(byte[] old, ByteBuffer receivedBuffer, int len) {
				if (old != null) {
					return old;
				}
				return readBytes(receivedBuffer, len);
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
								readIpLength = readInt(readIpLength, receivedBuffer);
								if (readIpLength < 0) {
									return;
								}
								readIp = readBytes(readIp, receivedBuffer, readIpLength);
								if (readIp == null) {
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
								// LOGGER.debug("SEND_WITH_ADDRESS {}:{} [{} bytes]", Address.ipToString(readIp), readPort, readLength);
								byte[] r = readBytes(receivedBuffer, readLength);
								if (r == null) {
									return;
								}
								InnerConnection receivedInnerConnection = connections.get(readConnectionId);
								if (receivedInnerConnection != null) {
									receivedInnerConnection.connection.received(new Address(readIp, readPort), ByteBuffer.wrap(r));
								}
								readConnectionId = -1;
								command = -1;
								readIpLength = -1;
								readIp = null;
								readPort = -1;
								readLength = -1;
								break;
							}
							case ProxyCommons.Commands.SEND_WITHOUT_ADDRESS: {
								readLength = readInt(readLength, receivedBuffer);
								if (readLength < 0) {
									return;
								}
								// LOGGER.debug("SEND_WITHOUT_ADDRESS [{} bytes]", readLength);
								byte[] r = readBytes(receivedBuffer, readLength);
								if (r == null) {
									return;
								}
								InnerConnection receivedInnerConnection = connections.get(readConnectionId);
								if (receivedInnerConnection != null) {
									receivedInnerConnection.connection.received(null, ByteBuffer.wrap(r));
								}
								readConnectionId = -1;
								command = -1;
								readLength = -1;
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
	}
	
	@Override
	public void close() {
		proxyExecutor.execute(new Runnable() {
			@Override
			public void run() {
				proxyConnector.close();
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
			public Connecter create(NinioProvider ninioProvider) {
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
			public Connecter create(NinioProvider ninioProvider) {
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
			public Connecter create(NinioProvider ninioProvider) {
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
			public Connecter create(NinioProvider ninioProvider) {
				return createConnector(new ProxyHeader(ProxyCommons.Types.UDP), null);
			}
		};
	}
	
	@Override
	public TcpdumpSocket.Builder tcpdump() {
		return new TcpdumpSocket.Builder() {
			private String interfaceId = null;
			private TcpdumpMode mode = null;
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
			public TcpdumpSocket.Builder mode(TcpdumpMode mode) {
				this.mode = mode;
				return this;
			}
			@Override
			public TcpdumpSocket.Builder rule(String rule) {
				this.rule = rule;
				return this;
			}
			
			@Override
			public Connecter create(NinioProvider ninioProvider) {
				return createConnector(new ProxyHeader(ProxyCommons.Types.TCPDUMP, ImmutableMap.of("interfaceId", interfaceId, "mode", mode.name(), "rule", rule)), null);
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
			public Connecter create(NinioProvider ninioProvider) {
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
			public WebsocketSocket.Builder with(HttpConnecter httpClient) {
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
			public Connecter create(NinioProvider ninioProvider) {
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
			public HttpSocket.Builder with(HttpConnecter httpClient) {
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
			public Connecter create(NinioProvider ninioProvider) {
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
						ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + connectAddress.ip.length + Ints.BYTES + Ints.BYTES + headerAsBytes.length);
						b.put((byte) ProxyCommons.Commands.CONNECT_WITH_ADDRESS);
						b.putInt(innerConnection.connectionId);
						b.putInt(connectAddress.ip.length);
						b.put(connectAddress.ip);
						b.putInt(connectAddress.port);
						b.putInt(headerAsBytes.length);
						b.put(headerAsBytes);
						b.flip();
						proxyConnector.send(null, b, sendCallback);
					}

					callback.connected(null);
				}
			});
		}
		
		@Override
		public void send(final Address sendAddress, final ByteBuffer sendBuffer, final SendCallback callback) {
			proxyExecutor.execute(new Runnable() {
				@Override
				public void run() {
					if (innerConnection.connection == null) {
						throw new IllegalStateException("send() must be called after connect()");
					}
					
					if (sendAddress == null) {
						// LOGGER.debug("-->SEND_WITHOUT_ADDRESS [{} bytes]", sendBuffer.remaining());
						ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + sendBuffer.remaining());
						b.put((byte) ProxyCommons.Commands.SEND_WITHOUT_ADDRESS);
						b.putInt(innerConnection.connectionId);
						b.putInt(sendBuffer.remaining());
						b.put(sendBuffer);
						b.flip();
						proxyConnector.send(null, b, callback);
					} else {
						// LOGGER.debug("-->SEND_WITH_ADDRESS {} [{} bytes]", sendAddress, sendBuffer.remaining());
						ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + sendAddress.ip.length + Ints.BYTES + Ints.BYTES + sendBuffer.remaining());
						b.put((byte) ProxyCommons.Commands.SEND_WITH_ADDRESS);
						b.putInt(innerConnection.connectionId);
						b.putInt(sendAddress.ip.length);
						b.put(sendAddress.ip);
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
					
					innerConnection.connection.closed();
				}
			});
		}
	}
}
