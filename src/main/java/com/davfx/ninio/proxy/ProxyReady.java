package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ClassThreadFactory;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueReady;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.util.ConfigUtils;
import com.davfx.util.Pair;
import com.typesafe.config.Config;

final class ProxyReady {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyReady.class);
	private static final Config CONFIG = ConfigUtils.load(ProxyReady.class);

	public static final double DEFAULT_CONNECTION_TIMEOUT = ConfigUtils.getDuration(CONFIG, "proxy.timeout.connection");
	public static final double DEFAULT_READ_TIMEOUT = ConfigUtils.getDuration(CONFIG, "proxy.timeout.read");

	private final Address proxyServerAddress;

	private final Object lock = new Object();
	private DataOutputStream currentOut = null;
	private Map<Integer, Pair<Address, ReadyConnection>> currentConnections;
	private int nextConnectionId;
	private double connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;
	private double readTimeout = DEFAULT_READ_TIMEOUT;
	private double throttleBytesPerSecond = 0d;
	private double throttleTimeStep = 0d;
	private long throttleBytesStep = 0L;
	
	private final ProxyUtils.ClientSide proxyUtils = ProxyUtils.client();
	
	private Executor executor = Executors.newSingleThreadExecutor(new ClassThreadFactory(ProxyReady.class));
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
	
	public ProxyReady throttle(double bytesPerSecond, double timeStep, long bytesStep) {
		throttleBytesPerSecond = bytesPerSecond;
		throttleTimeStep = timeStep;
		throttleBytesStep = bytesStep;
		return this;
	}

	public ProxyReady withConnectionTimeout(double connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
		return this;
	}
	public ProxyReady withReadTimeout(double readTimeout) {
		this.readTimeout = readTimeout;
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
			public void connect(Address address, ReadyConnection connection) {
				queue.check();
				
				final DataOutputStream out;
				final Map<Integer, Pair<Address, ReadyConnection>> connections;
				int connectionId;
				
				synchronized (lock) {
					if (currentOut == null) {
						final DataInputStream in;
						final Socket socket;
						final Throttle throttle = (throttleBytesPerSecond == 0d) ? null : new Throttle(throttleBytesPerSecond, throttleTimeStep, throttleBytesStep);
			
						try {
							
							socket = new Socket();
							socket.setKeepAlive(true);
							socket.setSoTimeout((int) (readTimeout * 1000d));
							InetSocketAddress a = new InetSocketAddress(proxyServerAddress.getHost(), proxyServerAddress.getPort());
							socket.connect(a, (int) (connectionTimeout * 1000d));
							
							OutputStream sout;
							InputStream sin;
							try {
								sout = socket.getOutputStream();
								sin = socket.getInputStream();
							} catch (IOException ee) {
								try {
									socket.close();
								} catch (IOException se) {
								}
								throw ee;
							}
							
							out = new DataOutputStream(sout);
							in = new DataInputStream(sin);

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
			
						connections = new HashMap<>();
			
						executor.execute(new Runnable() {
							@Override
							public void run() {
								listener.connected();
	
								while (true) {
									try {
										LOGGER.trace("Client waiting for connection ID");
										final int connectionId = in.readInt();
										int len = in.readInt();
										if (len < 0) {
											int command = -len;
											Pair<Address, ReadyConnection> currentConnection;
											synchronized (lock) {
												currentConnection = connections.get(connectionId);
											}
											if (command == ProxyCommons.COMMAND_ESTABLISH_CONNECTION) {
												if (currentConnection != null) {
													currentConnection.second.connected(new FailableCloseableByteBufferHandler() {
														@Override
														public void close() {
															synchronized (lock) {
																connections.remove(connectionId);
																LOGGER.trace("Connections size = {}", connections.size());
															}
															try {
																out.writeInt(connectionId);
																out.writeInt(0);
																out.flush();
															} catch (IOException ioe) {
																try {
																	out.close();
																} catch (IOException e) {
																}
																LOGGER.trace("Connection lost", ioe);
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
															int len = buffer.remaining();
															try {
																out.writeInt(connectionId);
																out.writeInt(len);
																out.write(buffer.array(), buffer.arrayOffset(), len);
																if (throttle != null) {
																	throttle.sent(len);
																}
																out.flush();
															} catch (IOException ioe) {
																try {
																	out.close();
																} catch (IOException e) {
																}
																LOGGER.trace("Connection lost", ioe);
															}
														}
													});
												}
											} else if (command == ProxyCommons.COMMAND_FAIL_CONNECTION) {
												if (currentConnection != null) {
													currentConnection.second.failed(new IOException("Failed"));
												}
											}
										} else if (len == 0) {
											Pair<Address, ReadyConnection> currentConnection;
											synchronized (lock) {
												currentConnection = connections.remove(connectionId);
												LOGGER.trace("Connections size = {}", connections.size());
											}
											if (currentConnection != null) {
												currentConnection.second.close();
											}
										} else {
											Pair<Address, ReadyConnection> currentConnection;
											synchronized (lock) {
												currentConnection = connections.get(connectionId);
											}
											final byte[] b = new byte[len];
											in.readFully(b);
											if (currentConnection != null) {
												currentConnection.second.handle(currentConnection.first, ByteBuffer.wrap(b));
											}
										}
									} catch (IOException ioe) {
										LOGGER.warn("Connection lost with server", ioe);
										break;
									}
								}
			
								try {
									socket.close();
								} catch (IOException e) {
								}
			
								List<ReadyConnection> toClose = new LinkedList<>();
								synchronized (lock) {
									currentOut = null;
									currentConnections = null;
									for (Pair<Address, ReadyConnection> c : connections.values()) {
										toClose.add(c.second);
									}
								}

								for (ReadyConnection c : toClose) {
									c.failed(new IOException("Connection lost"));
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
					
					connectionId = nextConnectionId;
					nextConnectionId++;
				}
				
				synchronized (lock) {
					connections.put(connectionId, new Pair<>(address, connection));
				}

				try {
					out.writeInt(connectionId);
					out.writeInt(-ProxyCommons.COMMAND_ESTABLISH_CONNECTION);
					out.writeUTF(address.getHost());
					out.writeInt(address.getPort());
					proxyUtils.write(connecterType, out);
					out.flush();
				} catch (IOException ioe) {
					connection.failed(new IOException("Connection to proxy failed", ioe));
				}
			}
		});
	}
}
