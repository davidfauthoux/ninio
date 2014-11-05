package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueReady;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.util.Pair;

final class ProxyReady {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyReady.class);

	private final Address proxyServerAddress;

	private DataOutputStream currentOut = null;
	private Map<Integer, Pair<Address, ReadyConnection>> currentConnections;
	private int nextConnectionId;
	
	private final ProxyUtils.ClientSide proxyUtils = ProxyUtils.client();
	
	private Executor executor = Executors.newSingleThreadExecutor();
	private ProxyListener listener = new ProxyListener() {
		@Override
		public void failed(IOException e) {
		}
		@Override
		public void disconnected() {
		}
		@Override
		public void connected() {
		}
	};
	
	public ProxyReady(Address proxyServerAddress) {
		this.proxyServerAddress = proxyServerAddress;
	}
	
	public ProxyReady withExecutor(Executor executor) {
		this.executor = executor;
		return this;
	}
	
	public ProxyReady listen(ProxyListener listener) {
		this.listener = listener;
		return this;
	}
	
	public ProxyReady override(String type, ProxyUtils.ClientSideConfigurator configurator) {
		proxyUtils.override(type, configurator);
		return this;
	}

	public Ready get(final Queue queue, final String connecterType) {
		return new QueueReady(queue, new Ready() {
			@Override
			public void connect(final Address address, ReadyConnection connection) {
				queue.check();
				final DataOutputStream out;
				final Map<Integer, Pair<Address, ReadyConnection>> connections;
		
				if (currentOut == null) {
					final DataInputStream in;
					final Socket socket;
		
					try {
						socket = new Socket(proxyServerAddress.getHost(), proxyServerAddress.getPort());
						try {
							out = new DataOutputStream(socket.getOutputStream());
							in = new DataInputStream(socket.getInputStream());
						} catch (IOException ee) {
							try {
								socket.close();
							} catch (IOException se) {
							}
							throw ee;
						}
					} catch (final IOException ioe) {
						connection.failed(new IOException("Connection to proxy cannot be established", ioe));
						executor.execute(new Runnable() {
							@Override
							public void run() {
								listener.failed(ioe);
							}
						});
						return;
					}
		
					connections = new ConcurrentHashMap<>();
		
					executor.execute(new Runnable() {
						@Override
						public void run() {
							listener.connected();

							while (true) {
								try {
									LOGGER.trace("Client waiting for connection ID");
									int connectionId = in.readInt();
									Pair<Address, ReadyConnection> currentConnection = connections.get(connectionId);
									int len = in.readInt();
									if (len < 0) {
										connections.remove(connectionId);
										if (currentConnection != null) {
											currentConnection.second.failed(new IOException("Failed"));
										}
									} else if (len == 0) {
										connections.remove(connectionId);
										if (currentConnection != null) {
											currentConnection.second.close();
										}
									} else {
										final byte[] b = new byte[len];
										in.readFully(b);
										if (currentConnection != null) {
											currentConnection.second.handle(address, ByteBuffer.wrap(b));
										}
									}
								} catch (IOException ioe) {
									LOGGER.warn("Connection lost");
									break;
								}
							}
		
							try {
								socket.close();
							} catch (IOException e) {
							}
		
							queue.post(new Runnable() {
								@Override
								public void run() {
									currentOut = null;
									currentConnections = null;
								}
							});
		
							for (Pair<Address, ReadyConnection> c : connections.values()) {
								c.second.close();
							}
							
							listener.disconnected();
						}
					});
					
					currentOut = out;
					currentConnections = connections;
					nextConnectionId = 0;
				} else {
					out = currentOut;
					connections = currentConnections;
				}
				
				final int connectionId = nextConnectionId;
				nextConnectionId++;
				
				connections.put(connectionId, new Pair<>(address, connection));

				try {
					out.writeInt(connectionId);
					out.writeInt(0);
					out.writeUTF(address.getHost());
					out.writeInt(address.getPort());
					proxyUtils.write(connecterType, out);
					out.flush();
				} catch (IOException ioe) {
					connection.failed(new IOException("Connection to proxy failed", ioe));
					return;
				}
		
				connection.connected(new FailableCloseableByteBufferHandler() {
					@Override
					public void close() {
						connections.remove(connectionId);
						try {
							out.writeInt(connectionId);
							out.writeInt(-1);
							out.flush();
						} catch (IOException ioe) {
							try {
								out.close();
							} catch (IOException e) {
							}
							LOGGER.warn("Connection lost", ioe);
						}
					}
					
					@Override
					public void failed(IOException e) {
						try {
							out.close();
						} catch (IOException ioe) {
						}
						LOGGER.warn("Connection cut", e);
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						if (!buffer.hasRemaining()) {
							return;
						}
						try {
							out.writeInt(connectionId);
							out.writeInt(buffer.remaining());
							out.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());  // Should always work
							out.flush();
						} catch (IOException ioe) {
							try {
								out.close();
							} catch (IOException e) {
							}
							LOGGER.warn("Connection lost", ioe);
						}
					}
				});
			}
		});
	}
}
