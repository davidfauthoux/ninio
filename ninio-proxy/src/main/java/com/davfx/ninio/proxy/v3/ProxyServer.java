package com.davfx.ninio.proxy.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Listening;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.NinioSocketBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.davfx.ninio.core.v3.SocketBuilder;
import com.google.common.base.Charsets;
import com.google.common.primitives.Ints;

public final class ProxyServer implements Listening {

	public static interface Builder extends NinioBuilder<ProxyServer> {
		Builder with(Executor executor);
		Builder listening(ProxyListening listening);
	}
	
	public static Builder builder() {
		return new Builder() {
			private Executor executor = null;
			private ProxyListening listening = new ProxyListening() {
				@Override
				public NinioSocketBuilder<?> create(Address address, String header) {
					return new NinioSocketBuilder<Void>() {
						@Override
						public Void closing(Closing closing) {
							return null;
						}
						@Override
						public Void failing(Failing failing) {
							failing.failed(new IOException());
							return null;
						}
						@Override
						public Void receiving(Receiver receiver) {
							return null;
						}
						@Override
						public Void connecting(Connecting connecting) {
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
	public void connecting(final Connector proxyConnector, SocketBuilder<?> builder) {
		final Map<Integer, Connector> connections = new HashMap<>();

		builder.closing(new Closing() {
			@Override
			public void closed() {
				closedRegisteredConnections(connections, new IOException("Connection to proxy lost"));
			}
		});
		builder.failing(new Failing() {
			@Override
			public void failed(IOException e) {
				closedRegisteredConnections(connections, new IOException("Connection to proxy lost", e));
			}
		});
		builder.receiving(new Receiver() {
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
								b.put((byte) ProxyCommons.Commands.CONNECT_WITH_ADDRESS);
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
						public void connected(Connector connector) {
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
						if (!receivedBuffer.hasRemaining()) {
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
						if (!receivedBuffer.hasRemaining()) {
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
								NinioSocketBuilder<?> externalBuilder = listening.create(a, header);
								externalBuilder.closing(closing);
								externalBuilder.failing(failing);
								externalBuilder.receiving(receiver);
								externalBuilder.connecting(connecting);
								Connector externalConnector = externalBuilder.create(queue);
								connections.put(connectionId, externalConnector);
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
								NinioSocketBuilder<?> externalBuilder = listening.create(null, header);
								externalBuilder.closing(closing);
								externalBuilder.failing(failing);
								externalBuilder.receiving(receiver);
								externalBuilder.connecting(connecting);
								Connector externalConnector = externalBuilder.create(queue);
								connections.put(connectionId, externalConnector);
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
		});
	}
}
