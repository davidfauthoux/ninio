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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.QueueReady;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.util.ClassThreadFactory;
import com.davfx.util.Pair;

final class ProxyReadyGenerator implements AutoCloseable, Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyReadyGenerator.class);
	
	private final Object lock = new Object();
	private DataOutputStream currentOut = null;
	private Map<Integer, Pair<Address, ReadyConnection>> currentConnections;
	private int nextConnectionId;
	
	private final Address proxyServerAddress;
	private final double connectionTimeout;
	private final double readTimeout;
	private final double throttleBytesPerSecond;
	private final double throttleTimeStep;
	private final long throttleBytesStep;
	
	private final ClientSide proxyClientSide = new BaseClientSide();
	
	private final ExecutorService executor = Executors.newSingleThreadExecutor(new ClassThreadFactory(ProxyReadyGenerator.class));
	private final ProxyListener listener;
	
	private boolean closed = false;
	
	public ProxyReadyGenerator(Address proxyServerAddress, double connectionTimeout, double readTimeout, double throttleBytesPerSecond, double throttleTimeStep, long throttleBytesStep, ProxyListener listener) {
		this.proxyServerAddress = proxyServerAddress;
		this.connectionTimeout = connectionTimeout;
		this.readTimeout = readTimeout;
		this.throttleBytesPerSecond = throttleBytesPerSecond;
		this.throttleTimeStep = throttleTimeStep;
		this.throttleBytesStep = throttleBytesStep;
		this.listener = listener;
		if (listener == null) {
			throw new NullPointerException();
		}
	}

	public ProxyReadyGenerator override(String type, ClientSideConfigurator configurator) {
		proxyClientSide.override(type, configurator);
		return this;
	}
	
	@Override
	public void close() {
		executor.shutdown();
		synchronized (lock) {
			closed = true;
			if (currentOut != null) {
				try {
					currentOut.close();
				} catch (IOException ioe) {
				}
			}
		}
	}

	public Ready get(final Queue queue, final String connecterType) {
		return new QueueReady(queue, new Ready() {
			@Override
			public void connect(Address address, ReadyConnection connection) {
				//%% queue.check();
				
				final DataOutputStream out;
				final Map<Integer, Pair<Address, ReadyConnection>> connections;
				int connectionId;
				
				synchronized (lock) {
					if (closed) {
						connection.failed(new IOException("Closed"));
						return;
					}
					
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
											if (command == ProxyCommons.Commands.ESTABLISH_CONNECTION) {
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
											} else if (command == ProxyCommons.Commands.FAIL_CONNECTION) {
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
										LOGGER.debug("Connection lost with server: {}", ioe.getMessage());
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
					connections.put(connectionId, new Pair<>(address, connection));
				}

				try {
					out.writeInt(connectionId);
					out.writeInt(-ProxyCommons.Commands.ESTABLISH_CONNECTION);
					out.writeUTF(address.getHost());
					out.writeInt(address.getPort());
					proxyClientSide.write(connecterType, out);
					out.flush();
				} catch (IOException ioe) {
					LOGGER.error("Could not establish connection", ioe);
					// Close the socket
					try {
						out.close();
					} catch (IOException e) {
					}
				}
			}
		});
	}
}
