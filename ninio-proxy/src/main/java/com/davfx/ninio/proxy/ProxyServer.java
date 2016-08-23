package com.davfx.ninio.proxy;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connected;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.RawSocket;
import com.davfx.ninio.core.SecureSocketBuilder;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.TcpdumpSocket;
import com.davfx.ninio.core.Trust;
import com.davfx.ninio.core.UdpSocket;
import com.davfx.ninio.dns.DnsClient;
import com.davfx.ninio.dns.DnsConnecter;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpConnecter;
import com.davfx.ninio.http.HttpSocket;
import com.davfx.ninio.http.WebsocketSocket;
import com.davfx.ninio.util.SerialExecutor;
import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;

public final class ProxyServer implements Listening {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServer.class);

	public static NinioBuilder<Disconnectable> defaultServer(final Address address, final ProxyListening listening) {
		return new NinioBuilder<Disconnectable>() {
			public Disconnectable create(final Queue queue) {
				final Trust trust = new Trust();
				final Executor executor = new SerialExecutor(ProxyServer.class);
				final DnsConnecter dnsClient = DnsClient.builder().with(executor).create(queue);
				final HttpConnecter httpClient = HttpClient.builder().with(dnsClient).with(executor).create(queue);
				
				final ProxyServer.Builder proxyServerBuilder = ProxyServer.builder().with(executor).listening(new ProxyListening() {
					@Override
					public void connected(Address address) {
						if (listening != null) {
							listening.connected(address);
						}
					}
					@Override
					public void failed(IOException e) {
						if (listening != null) {
							listening.failed(e);
						}
					}
					@Override
					public void closed() {
						if (listening != null) {
							listening.closed();
						}
					}
					
					@Override
					public NinioBuilder<Connecter> create(Address address, String header) {
						ProxyHeader h = ProxyHeader.of(header);
						
						if (h.type.equals(ProxyCommons.Types.TCP)) {
							return TcpSocket.builder().to(address);
						}
						if (h.type.equals(ProxyCommons.Types.UDP)) {
							return UdpSocket.builder();
						}
						if (h.type.equals(ProxyCommons.Types.TCPDUMP)) {
							return TcpdumpSocket.builder().on(h.parameters.get("interfaceId")).rule(h.parameters.get("rule"));
						}
						if (h.type.equals(ProxyCommons.Types.SSL)) {
							return new SecureSocketBuilder(TcpSocket.builder()).trust(trust).with(executor).to(address);
						}
						if (h.type.equals(ProxyCommons.Types.RAW)) {
							ProtocolFamily family = "6".equals(h.parameters.get("family")) ? StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
							int protocol = Integer.parseInt(h.parameters.get("protocol"));
							return RawSocket.builder().family(family).protocol(protocol);
						}
						if (h.type.equals(ProxyCommons.Types.WEBSOCKET)) {
							return WebsocketSocket.builder().to(address).with(httpClient).route(h.parameters.get("route"));
						}
						if (h.type.equals(ProxyCommons.Types.HTTP)) {
							return HttpSocket.builder().to(address).with(httpClient).route(h.parameters.get("route"));
						}
						
						if (listening == null) {
							return null;
						}
						
						return listening.create(address, header);
					}
				});

				final Listener server = TcpSocketServer.builder().bind(address).create(queue);
				server.listen(proxyServerBuilder.create(queue));
				return new Disconnectable() {
					@Override
					public void close() {
						server.close();
						httpClient.close();
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
			private ProxyListening listening = null;

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
				if (listening == null) {
					throw new NullPointerException("listening");
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
	
	private void closedRegisteredConnections(Map<Integer, Connecter> connections, IOException ioe) {
		for (final Connecter c : connections.values()) {
			c.close();
		}
		connections.clear();
	}

	@Override
	public Connection connecting(final Connected proxyConnector) {
		final Map<Integer, Connecter> connections = new HashMap<>();

		return new Connection() {
			private ByteBuffer readByteBuffer;

			private int readConnectionId = -1;
			private int command = -1;

			private int readIpLength = -1;
			private byte[] readIp = null;
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
			private byte[] readBytes(byte[] old, ByteBuffer receivedBuffer, int len) {
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
						byte[] b = readByteBuffer.array();
						readByteBuffer = null;
						return b;
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
					
					final SendCallback sendCallback = new SendCallback() {
						@Override
						public void failed(IOException e) {
							proxyConnector.close();
						}
						@Override
						public void sent() {
						}
					};
					
					final int connectionId = readConnectionId;
					final Connection connection = new Connection() {
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
	
							proxyConnector.send(null, b, sendCallback);
						}

						@Override
						public void failed(IOException e) {
							closed();
						}

						@Override
						public void received(Address receivedAddress, ByteBuffer receivedBuffer) {
							if (receivedAddress == null) {
								ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + receivedBuffer.remaining());
								b.put((byte) ProxyCommons.Commands.SEND_WITHOUT_ADDRESS);
								b.putInt(connectionId);
								b.putInt(receivedBuffer.remaining());
								b.put(receivedBuffer);
								b.flip();
								proxyConnector.send(null, b, sendCallback);
							} else {
								ByteBuffer b = ByteBuffer.allocate(1 + Ints.BYTES + Ints.BYTES + receivedAddress.ip.length + Ints.BYTES + Ints.BYTES + receivedBuffer.remaining());
								b.put((byte) ProxyCommons.Commands.SEND_WITH_ADDRESS);
								b.putInt(connectionId);
								b.putInt(receivedAddress.ip.length);
								b.put(receivedAddress.ip);
								b.putInt(receivedAddress.port);
								b.putInt(receivedBuffer.remaining());
								b.put(receivedBuffer);
								b.flip();
								proxyConnector.send(null, b, sendCallback);
							}
						}

						@Override
						public void connected(Address address) {
						}
					};
					
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
						int len = readLength;
						if (receivedBuffer.remaining() < len) {
							len = receivedBuffer.remaining();
						}
						final ByteBuffer b = receivedBuffer.duplicate();
						b.limit(b.position() + len);
						final Address a = new Address(readIp, readPort);
						proxyExecutor.execute(new Runnable() {
							@Override
							public void run() {
								Connecter receivedInnerConnection = connections.get(connectionId);
								if (receivedInnerConnection != null) {
									receivedInnerConnection.send(a, b, sendCallback);
								}
							}
						});
						receivedBuffer.position(receivedBuffer.position() + len);
						readLength -= len;
						if (readLength == 0) {
							readConnectionId = -1;
							command = -1;
							readIpLength = -1;
							readIp = null;
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
								Connecter receivedInnerConnection = connections.get(connectionId);
								if (receivedInnerConnection != null) {
									receivedInnerConnection.send(null, b, sendCallback);
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
								Connecter receivedInnerConnection = connections.remove(connectionId);
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
						readHeaderLength = readInt(readHeaderLength, receivedBuffer);
						if (readHeaderLength < 0) {
							return;
						}
						readHeader = readString(readHeader, receivedBuffer, readHeaderLength);
						if (readHeader == null) {
							return;
						}
						
						final String header = readHeader;
						final Address a = new Address(readIp, readPort);
						proxyExecutor.execute(new Runnable() {
							@Override
							public void run() {
								NinioBuilder<Connecter> externalBuilder = listening.create(a, header);
								if (externalBuilder == null) {
									LOGGER.error("Unknown header (CONNECT_WITH_ADDRESS): {}", header);
								} else {
									Connecter externalConnector = externalBuilder.create(queue);
									externalConnector.connect(connection);
									connections.put(connectionId, externalConnector);
								}
							}
						});
						
						readConnectionId = -1;
						command = -1;
						readIpLength = -1;
						readIp = null;
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
								NinioBuilder<Connecter> externalBuilder = listening.create(null, header);
								if (externalBuilder == null) {
									LOGGER.error("Unknown header (CONNECT_WITHOUT_ADDRESS): {}", header);
								} else {
									Connecter externalConnector = externalBuilder.create(queue);
									externalConnector.connect(connection);
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
			
			@Override
			public void failed(IOException e) {
				closedRegisteredConnections(connections, new IOException("Connection to proxy lost", e));
			}
			
			@Override
			public void closed() {
				closedRegisteredConnections(connections, new IOException("Connection to proxy lost"));
			}
			
			@Override
			public void connected(Address address) {
			}
		};
	}
	
	@Override
	public void closed() {
		listening.closed();
	}
	@Override
	public void connected(Address address) {
		listening.connected(address);
	}
	@Override
	public void failed(IOException e) {
		listening.failed(e);
	}
}
