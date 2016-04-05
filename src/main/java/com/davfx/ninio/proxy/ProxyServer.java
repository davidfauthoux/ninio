package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ClassThreadFactory;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueReady;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.util.ConfigUtils;
import com.davfx.util.Mutable;
import com.davfx.util.Pair;
import com.typesafe.config.Config;

public final class ProxyServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServer.class);
	private static final Config CONFIG = ConfigUtils.load(ProxyServer.class);

	public static void main(String[] args) throws Exception {
		ProxyServer server = new ProxyServer(CONFIG.getInt("proxy.port"), CONFIG.getInt("proxy.maxSimultaneousClients"));
		for (Config c : CONFIG.getConfigList("proxy.forward")) {
			server.override(c.getString("type"), Forward.forward(new Address(c.getString("host"), c.getInt("port"))));
		}
		for (String host : CONFIG.getStringList("proxy.filter")) {
			server.filter(host);
		}
		server.start();
	}
	
	private static final ScheduledExecutorService FLUSH_EXECUTOR = Executors.newSingleThreadScheduledExecutor();
	private static final double FLUSH_PERIOD = 1d;
	
	public static final double DEFAULT_READ_TIMEOUT = ConfigUtils.getDuration(CONFIG, "proxy.timeout.read");
	
	private final int port;
	
	private final Queue queue;
	private final ProxyUtils.ServerSide proxyUtils;
	private final ExecutorService clientExecutor;
	private final Set<String> hostsToFilter = new HashSet<>();
	
	public ProxyServer(int port, int maxNumberOfSimultaneousClients) throws IOException {
		queue = new Queue();
		proxyUtils = ProxyUtils.server(queue);
		this.port = port;
		LOGGER.debug("Proxy server on port {}", port);
		clientExecutor = Executors.newFixedThreadPool(maxNumberOfSimultaneousClients, new ClassThreadFactory(ProxyServer.class, "read"));
	}

	public ProxyServer override(String type, ProxyUtils.ServerSideConfigurator configurator) {
		proxyUtils.override(type, configurator);
		return this;
	}
	
	public ProxyServer filter(String host) {
		LOGGER.debug("Will filter out: {}", host);
		hostsToFilter.add(host);
		return this;
	}
	
	public void start() {
		Executors.newSingleThreadExecutor(new ClassThreadFactory(ProxyServer.class, "listen")).execute(new Runnable() {
			@Override
			public void run() {
				try (ServerSocket ss = new ServerSocket(port)) {
					while (true) {
						final Socket socket = ss.accept();
						socket.setKeepAlive(true);
						socket.setSoTimeout((int) (DEFAULT_READ_TIMEOUT * 1000d));

						clientExecutor.execute(new Runnable() {
							@Override
							public void run() {
								final Map<Integer, Pair<Address, CloseableByteBufferHandler>> establishedConnections = new HashMap<>();
								final Mutable<Boolean> closed = new Mutable<>(false);

								try {
									final DataOutputStream out = new DataOutputStream(socket.getOutputStream());
									DataInputStream in = new DataInputStream(socket.getInputStream());
									LOGGER.debug("Accepted connection from: {}", socket.getInetAddress());

									ScheduledFuture<?> flushing = FLUSH_EXECUTOR.scheduleAtFixedRate(new Runnable() {
										@Override
										public void run() {
											try {
												out.flush();
											} catch (IOException e) {
											}
										}
									}, 0L, (long) (FLUSH_PERIOD * 1000d), TimeUnit.MILLISECONDS);
									
									try {
										while (true) {
											LOGGER.trace("Server waiting for connection ID");
											final int connectionId = in.readInt();
											int len = in.readInt();
											if (len < 0) {
												int command = -len;
												if (command == ProxyCommons.COMMAND_ESTABLISH_CONNECTION) {
													final Address address = new Address(in.readUTF(), in.readInt());
													ReadyFactory factory = proxyUtils.read(in);
													Ready r = new QueueReady(queue, factory.create(queue));
													r.connect(address, new ReadyConnection() {
														@Override
														public void failed(IOException e) {
															LOGGER.warn("Could not connect to {}", address, e);
															try {
																out.writeInt(connectionId);
																out.writeInt(-ProxyCommons.COMMAND_FAIL_CONNECTION);
																// out.flush();
															} catch (IOException ioe) {
																try {
																	out.close();
																} catch (IOException se) {
																}
															}
														}
														
														@Override
														public void connected(FailableCloseableByteBufferHandler write) {
															synchronized (establishedConnections) {
																if (closed.get()) {
																	write.close();
																	return;
																}
																establishedConnections.put(connectionId, new Pair<Address, CloseableByteBufferHandler>(address, write));
															}
	
															try {
																out.writeInt(connectionId);
																out.writeInt(-ProxyCommons.COMMAND_ESTABLISH_CONNECTION);
																// out.flush();
															} catch (IOException ioe) {
																try {
																	out.close();
																} catch (IOException se) {
																}
															}
														}
														
														@Override
														public void close() {
															try {
																out.writeInt(connectionId);
																out.writeInt(0);
																// out.flush();
															} catch (IOException ioe) {
																try {
																	out.close();
																} catch (IOException se) {
																}
															}
														}
														
														@Override
														public void handle(Address address, ByteBuffer buffer) {
															if (!buffer.hasRemaining()) {
																return;
															}
															try {
																out.writeInt(connectionId);
																out.writeInt(buffer.remaining());
																out.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
																// out.flush();
															} catch (IOException ioe) {
																try {
																	out.close();
																} catch (IOException se) {
																}
															}
															buffer.position(buffer.position() + buffer.remaining());
														}
													});
												}
											} else if (len == 0) {
												Pair<Address, CloseableByteBufferHandler> connection;
												synchronized (establishedConnections) {
													connection = establishedConnections.remove(connectionId);
												}
												if (connection != null) {
													if (connection.second != null) {
														connection.second.close();
													}
												}
											} else {
												byte[] b = new byte[len];
												in.readFully(b);
												Pair<Address, CloseableByteBufferHandler> connection;
												synchronized (establishedConnections) {
													connection = establishedConnections.get(connectionId);
												}
												if (connection != null) {
													if (!hostsToFilter.contains(connection.first.getHost())) {
														if (connection.second != null) {
															connection.second.handle(connection.first, ByteBuffer.wrap(b));
														}
													}
												}
											}
										}
									} finally {
										flushing.cancel(false);
									}
								} catch (IOException e) {
									LOGGER.info("Socket closed by peer", e);

									try {
										socket.close();
									} catch (IOException ioe) {
									}
									
									List<CloseableByteBufferHandler> toClose = new LinkedList<>();
									synchronized (establishedConnections) {
										closed.set(true);
										for (Pair<Address, CloseableByteBufferHandler> connection : establishedConnections.values()) {
											if (connection.second != null) {
												toClose.add(connection.second);
											}
										}
									}
									for (CloseableByteBufferHandler c : toClose) {
										c.close();
									}
								}
							}
						});
					}
				} catch (IOException ioe) {
					LOGGER.error("Could not open server socket", ioe.getMessage()); // We don't want the stacktrace here because it's usually a port-already-in-use problem
				}
			}
		});
	}
}
