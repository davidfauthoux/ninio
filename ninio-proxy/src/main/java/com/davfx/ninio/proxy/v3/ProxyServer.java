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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.ExecutorUtils;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Listening;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.RawSocket;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.SecureSocketBuilder;
import com.davfx.ninio.core.v3.TcpSocket;
import com.davfx.ninio.core.v3.TcpSocketServer;
import com.davfx.ninio.core.v3.Trust;
import com.davfx.ninio.core.v3.UdpSocket;
import com.davfx.ninio.http.v3.HttpClient;
import com.davfx.ninio.http.v3.HttpSocket;
import com.davfx.ninio.http.v3.WebsocketSocket;
import com.davfx.util.ClassThreadFactory;
import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;

public final class ProxyServer implements Listening {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServer.class);

	public static NinioBuilder<Disconnectable> defaultServer(final Address address, final ProxyListening listening) {
		return new NinioBuilder<Disconnectable>() {
			public Disconnectable create(Queue queue) {
				final Trust trust = new Trust();
				final ExecutorService executor = Executors.newSingleThreadExecutor(new ClassThreadFactory(ProxyServer.class, true));
				final HttpClient httpClient = HttpClient.builder().with(executor).create(queue);
				final Disconnectable server = TcpSocketServer.builder().bind(address).listening(ProxyServer.builder().with(executor).listening(new ProxyListening() {
					@Override
					public ProxyListening.Builder create(Address address, String header) {
						if (header.startsWith(ProxyCommons.Types.TCP)) {
							final TcpSocket.Builder b = TcpSocket.builder().to(address);
							return new ProxyListening.Builder() {
								@Override
								public Builder closing(Closing closing) {
									b.closing(closing);
									return this;
								}
								@Override
								public Builder connecting(Connecting connecting) {
									b.connecting(connecting);
									return this;
								}
								@Override
								public Builder failing(Failing failing) {
									b.failing(failing);
									return this;
								}
								@Override
								public Builder receiving(Receiver receiver) {
									b.receiving(receiver);
									return this;
								}
								@Override
								public Connector create(Queue queue) {
									return b.create(queue);
								}
							};
						}
						if (header.startsWith(ProxyCommons.Types.UDP)) {
							final UdpSocket.Builder b = UdpSocket.builder();
							return new ProxyListening.Builder() {
								@Override
								public Builder closing(Closing closing) {
									b.closing(closing);
									return this;
								}
								@Override
								public Builder connecting(Connecting connecting) {
									b.connecting(connecting);
									return this;
								}
								@Override
								public Builder failing(Failing failing) {
									b.failing(failing);
									return this;
								}
								@Override
								public Builder receiving(Receiver receiver) {
									b.receiving(receiver);
									return this;
								}
								@Override
								public Connector create(Queue queue) {
									return b.create(queue);
								}
							};
						}
						if (header.startsWith(ProxyCommons.Types.SSL)) {
							final TcpSocket.Builder b = new SecureSocketBuilder(TcpSocket.builder()).trust(trust).with(executor).to(address);
							return new ProxyListening.Builder() {
								@Override
								public Builder closing(Closing closing) {
									b.closing(closing);
									return this;
								}
								@Override
								public Builder connecting(Connecting connecting) {
									b.connecting(connecting);
									return this;
								}
								@Override
								public Builder failing(Failing failing) {
									b.failing(failing);
									return this;
								}
								@Override
								public Builder receiving(Receiver receiver) {
									b.receiving(receiver);
									return this;
								}
								@Override
								public Connector create(Queue queue) {
									return b.create(queue);
								}
							};
						}
						if (header.startsWith(ProxyCommons.Types.RAW)) {
							ProtocolFamily family = (header.charAt(ProxyCommons.Types.RAW.length()) == '4') ? StandardProtocolFamily.INET : StandardProtocolFamily.INET6;
							int protocol = Integer.parseInt(header.substring(ProxyCommons.Types.RAW.length() + 1));
							final RawSocket.Builder b = RawSocket.builder().family(family).protocol(protocol);
							return new ProxyListening.Builder() {
								@Override
								public Builder closing(Closing closing) {
									b.closing(closing);
									return this;
								}
								@Override
								public Builder connecting(Connecting connecting) {
									b.connecting(connecting);
									return this;
								}
								@Override
								public Builder failing(Failing failing) {
									b.failing(failing);
									return this;
								}
								@Override
								public Builder receiving(Receiver receiver) {
									b.receiving(receiver);
									return this;
								}
								@Override
								public Connector create(Queue queue) {
									return b.create(queue);
								}
							};
						}
						if (header.startsWith(ProxyCommons.Types.WEBSOCKET)) {
							final WebsocketSocket.Builder b = WebsocketSocket.builder().to(address).with(httpClient).route(header.substring(ProxyCommons.Types.WEBSOCKET.length()));
							return new ProxyListening.Builder() {
								@Override
								public Builder closing(Closing closing) {
									b.closing(closing);
									return this;
								}
								@Override
								public Builder connecting(Connecting connecting) {
									b.connecting(connecting);
									return this;
								}
								@Override
								public Builder failing(Failing failing) {
									b.failing(failing);
									return this;
								}
								@Override
								public Builder receiving(Receiver receiver) {
									b.receiving(receiver);
									return this;
								}
								@Override
								public Connector create(Queue queue) {
									return b.create(queue);
								}
							};
						}
						if (header.startsWith(ProxyCommons.Types.HTTP)) {
							final HttpSocket.Builder b = HttpSocket.builder().to(address).with(httpClient).route(header.substring(ProxyCommons.Types.HTTP.length()));
							return new ProxyListening.Builder() {
								@Override
								public Builder closing(Closing closing) {
									b.closing(closing);
									return this;
								}
								@Override
								public Builder connecting(Connecting connecting) {
									b.connecting(connecting);
									return this;
								}
								@Override
								public Builder failing(Failing failing) {
									b.failing(failing);
									return this;
								}
								@Override
								public Builder receiving(Receiver receiver) {
									b.receiving(receiver);
									return this;
								}
								@Override
								public Connector create(Queue queue) {
									return b.create(queue);
								}
							};
						}
						if (listening == null) {
							return null;
						}
						return listening.create(address, header);
					}
				}).create(queue)).create(queue);
				return new Disconnectable() {
					@Override
					public void close() {
						server.close();
						httpClient.close();
						ExecutorUtils.shutdown(executor);
					}
				};
			}
		};
	}
	
	public static interface Builder extends NinioBuilder<ProxyServer> {
		Builder with(Executor executor);
		Builder listening(ProxyListening listening);
	}
	
	public static Builder builder() {
		return new Builder() {
			private Executor executor = null;
			private ProxyListening listening = new ProxyListening() {
				@Override
				public Builder create(Address address, String header) {
					return new Builder() {
						@Override
						public Builder closing(Closing closing) {
							return null;
						}
						@Override
						public Builder failing(Failing failing) {
							failing.failed(new IOException());
							return null;
						}
						@Override
						public Builder receiving(Receiver receiver) {
							return null;
						}
						@Override
						public Builder connecting(Connecting connecting) {
							return null;
						}
						@Override
						public Connector create(Queue queue) {
							return new Connector() {
								@Override
								public void close() {
								}
								@Override
								public Connector send(Address address, ByteBuffer buffer) {
									return this;
								}
							};
						}
					};
				}
			};

			@Override
			public Builder with(Executor executor) {
				this.executor = executor;
				return this;
			}
			
			@Override
			public Builder listening(ProxyListening listening) {
				this.listening = listening;
				return this;
			}

			@Override
			public ProxyServer create(Queue queue) {
				if (executor == null) {
					throw new NullPointerException("executor");
				}
				return new ProxyServer(queue, executor, listening);
			}
		};
	}
	
	private final Queue queue;
	private final Executor proxyExecutor;
	private final ProxyListening listening;

	private ProxyServer(Queue queue, Executor proxyExecutor, ProxyListening listening) {
		this.queue = queue;
		this.proxyExecutor = proxyExecutor;
		this.listening = listening;
	}
	
	private void closedRegisteredConnections(Map<Integer, Connector> connections, IOException ioe) {
		for (final Connector c : connections.values()) {
			c.close();
		}
		connections.clear();
	}

	@Override
	public Connection connecting(Address from, final Connector proxyConnector) {
		final Map<Integer, Connector> connections = new HashMap<>();

		return new Listening.Connection() {
			
			@Override
			public Receiver receiver() {
				return new Receiver() {
					private ByteBuffer readByteBuffer;

					private int readConnectionId = -1;
					private int command = -1;

					private int readHostLength = -1;
					private String readHost = null;
					private int readPort = -1;
					private int readLength = -1;
					private int readHeaderLength = -1;
					private String readHeader = null;

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
					public void received(Connector receivedConnector, Address receivedAddress, ByteBuffer receivedBuffer) {
						while (true) {
							command = readByte(command, receivedBuffer);
							if (command < 0) {
								return;
							}
			
							readConnectionId = readInt(readConnectionId, receivedBuffer);
							if (readConnectionId < 0) {
								return;
							}
							
							final int connectionId = readConnectionId;
							final Closing closing = new Closing() {
								@Override
								public void closed() {
									proxyExecutor.execute(new Runnable() {
										@Override
										public void run() {
											connections.remove(connectionId);
										}
									});
			
									ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES);
									b.put((byte) ProxyCommons.Commands.CLOSE);
									b.putInt(connectionId);
									b.flip();
			
									proxyConnector.send(null, b);
								}
							};
							final Failing failing = new Failing() {
								@Override
								public void failed(IOException e) {
									closing.closed();
								}
							};
							final Receiver receiver = new Receiver() {
								@Override
								public void received(Connector connector, Address receivedAddress, ByteBuffer receivedBuffer) {
									if (receivedAddress == null) {
										ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + receivedBuffer.remaining());
										b.put((byte) ProxyCommons.Commands.SEND_WITHOUT_ADDRESS);
										b.putInt(connectionId);
										b.putInt(receivedBuffer.remaining());
										b.put(receivedBuffer);
										b.flip();
										proxyConnector.send(null, b);
									} else {
										byte[] hostAsBytes = receivedAddress.getHost().getBytes(Charsets.UTF_8);
										ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + hostAsBytes.length + Ints.BYTES + Ints.BYTES + receivedBuffer.remaining());
										b.put((byte) ProxyCommons.Commands.SEND_WITH_ADDRESS);
										b.putInt(connectionId);
										b.putInt(hostAsBytes.length);
										b.put(hostAsBytes);
										b.putInt(receivedAddress.getPort());
										b.putInt(receivedBuffer.remaining());
										b.put(receivedBuffer);
										b.flip();
										proxyConnector.send(null, b);
									}
								}
							};
							final Connecting connecting = new Connecting() {
								@Override
								public void connected(Address to, Connector connector) {
								}
							};
							
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
								final ByteBuffer b = receivedBuffer.duplicate();
								b.limit(b.position() + len);
								final Address a = new Address(readHost, readPort);
								proxyExecutor.execute(new Runnable() {
									@Override
									public void run() {
										Connector receivedInnerConnection = connections.get(connectionId);
										if (receivedInnerConnection != null) {
											receivedInnerConnection.send(a, b);
										}
									}
								});
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
								final ByteBuffer b = receivedBuffer.duplicate();
								b.limit(b.position() + len);
								proxyExecutor.execute(new Runnable() {
									@Override
									public void run() {
										Connector receivedInnerConnection = connections.get(connectionId);
										if (receivedInnerConnection != null) {
											receivedInnerConnection.send(null, b);
										}
									}
								});
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
								proxyExecutor.execute(new Runnable() {
									@Override
									public void run() {
										Connector receivedInnerConnection = connections.remove(connectionId);
										if (receivedInnerConnection != null) {
											receivedInnerConnection.close();
										}
									}
								});
								readConnectionId = -1;
								command = -1;
								break;
							}
							case ProxyCommons.Commands.CONNECT_WITH_ADDRESS: {
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
								readHeaderLength = readInt(readHeaderLength, receivedBuffer);
								if (readHeaderLength < 0) {
									return;
								}
								readHeader = readString(readHeader, receivedBuffer, readHeaderLength);
								if (readHeader == null) {
									return;
								}
								
								final String header = readHeader;
								final Address a = new Address(readHost, readPort);
								proxyExecutor.execute(new Runnable() {
									@Override
									public void run() {
										ProxyListening.Builder externalBuilder = listening.create(a, header);
										if (externalBuilder == null) {
											LOGGER.error("Unknown header (CONNECT_WITH_ADDRESS): {}", header);
										} else {
											externalBuilder.closing(closing);
											externalBuilder.failing(failing);
											externalBuilder.receiving(receiver);
											externalBuilder.connecting(connecting);
											Connector externalConnector = externalBuilder.create(queue);
											connections.put(connectionId, externalConnector);
										}
									}
								});
								
								readConnectionId = -1;
								command = -1;
								readHostLength = -1;
								readHost = null;
								readPort = -1;
								readLength = -1;
								readHeaderLength = -1;
								readHeader = null;
								break;
							}
							case ProxyCommons.Commands.CONNECT_WITHOUT_ADDRESS: {
								readHeaderLength = readInt(readHeaderLength, receivedBuffer);
								if (readHeaderLength < 0) {
									return;
								}
								readHeader = readString(readHeader, receivedBuffer, readHeaderLength);
								if (readHeader == null) {
									return;
								}
			
								final String header = readHeader;
								proxyExecutor.execute(new Runnable() {
									@Override
									public void run() {
										ProxyListening.Builder externalBuilder = listening.create(null, header);
										if (externalBuilder == null) {
											LOGGER.error("Unknown header (CONNECT_WITHOUT_ADDRESS): {}", header);
										} else {
											externalBuilder.closing(closing);
											externalBuilder.failing(failing);
											externalBuilder.receiving(receiver);
											externalBuilder.connecting(connecting);
											Connector externalConnector = externalBuilder.create(queue);
											connections.put(connectionId, externalConnector);
										}
									}
								});
			
								readConnectionId = -1;
								command = -1;
								readLength = -1;
								readHeaderLength = -1;
								readHeader = null;
								break;
							}
							}
						}
					}
				};
			}
			
			@Override
			public Failing failing() {
				return new Failing() {
					@Override
					public void failed(IOException e) {
						closedRegisteredConnections(connections, new IOException("Connection to proxy lost", e));
					}
				};
			}
			
			@Override
			public Connecting connecting() {
				return null;
			}
			
			@Override
			public Closing closing() {
				return new Closing() {
					@Override
					public void closed() {
						closedRegisteredConnections(connections, new IOException("Connection to proxy lost"));
					}
				};
			}
		};
	}
}
