package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.util.ClassThreadFactory;
import com.davfx.util.ConfigUtils;
import com.davfx.util.Pair;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ProxyServer implements AutoCloseable, Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServer.class);
	
	private static final Config CONFIG = ConfigFactory.load(ProxyServer.class.getClassLoader());

	public static final double READ_TIMEOUT = ConfigUtils.getDuration(CONFIG, "ninio.proxy.timeout.read");
	public static final int BACKLOG = CONFIG.getInt("ninio.proxy.backlog");
	
	private final BaseServerSide proxyServerSide;
	private final ExecutorService listenExecutor;
	private final ExecutorService clientExecutor;
	private final Set<Address> addressesToFilter = new HashSet<>();
	private final ServerSocket serverSocket;
	
	private final List<Socket> sockets = new LinkedList<>();
	private boolean closed = false;
	
	public ProxyServer(Queue queue, Address address, int maxNumberOfSimultaneousClients) throws IOException {
		proxyServerSide = new BaseServerSide(queue);
		LOGGER.debug("Proxy server on {}", address);
		listenExecutor = Executors.newSingleThreadExecutor(new ClassThreadFactory(ProxyServer.class, "listen"));
		clientExecutor = Executors.newFixedThreadPool(maxNumberOfSimultaneousClients, new ClassThreadFactory(ProxyServer.class, "read"));
		serverSocket = new ServerSocket(address.getPort(), BACKLOG, InetAddress.getByName(address.getHost()));
	}

	private void add(Socket socket) throws IOException {
		synchronized (sockets) {
			if (closed) {
				try {
					socket.close();
				} catch (IOException ioe) {
				}
				throw new IOException("Closed");
			}
			sockets.add(socket);
		}
	}
	
	@Override
	public void close() {
		LOGGER.debug("Closing proxy server socket");
		try {
			serverSocket.close();
		} catch (IOException ioe) {
		}
		
		synchronized (sockets) {
			closed = true;
			for (Socket s : sockets) {
				try {
					s.close();
				} catch (IOException ioe) {
				}
			}
		}

		listenExecutor.shutdown();
		clientExecutor.shutdown();
		proxyServerSide.close();
	}
	
	public ReadyFactory datagramReadyFactory() {
		return proxyServerSide.datagramReadyFactory();
	}
	public ReadyFactory socketReadyFactory() {
		return proxyServerSide.socketReadyFactory();
	}

	public ProxyServer override(String type, ServerSideConfigurator configurator) {
		proxyServerSide.override(type, configurator);
		return this;
	}
	
	public ProxyServer filter(Address address) {
		LOGGER.debug("Will filter out: {}", address);
		addressesToFilter.add(address);
		return this;
	}
	
	public ProxyServer start() {
		listenExecutor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					while (true) {
						final Socket socket = serverSocket.accept();
						add(socket);
						try {
							socket.setKeepAlive(true);
							socket.setSoTimeout((int) (READ_TIMEOUT * 1000d));
						} catch (IOException e) {
							LOGGER.error("Could not configure client socket", e);
						}
	
						clientExecutor.execute(new Runnable() {
							@Override
							public void run() {
								final Map<Integer, Pair<Address, CloseableByteBufferHandler>> establishedConnections = new HashMap<>();
								final boolean[] closed = new boolean[] { false };
	
								try {
									OutputStream sout;
									InputStream sin;
									try {
										sout = new GZIPOutputStream(socket.getOutputStream(), true);
										sin = new GZIPInputStream(socket.getInputStream());
									} catch (IOException ee) {
										try {
											socket.close();
										} catch (IOException se) {
										}
										throw ee;
									}
									
									final DataOutputStream out = new DataOutputStream(sout);
									DataInputStream in = new DataInputStream(sin);

									LOGGER.debug("Accepted connection from: {}", socket.getInetAddress());
	
									while (true) {
										LOGGER.trace("Server waiting for connection ID");
										final int connectionId = in.readInt();
										LOGGER.trace("Server received connection ID: {}", connectionId);
										int len = in.readInt();
										if (len < 0) {
											int command = -len;
											if (command == ProxyCommons.Commands.ESTABLISH_CONNECTION) {
												final Address address = in.readBoolean() ? new Address(in.readUTF(), in.readInt()) : null;
												ReadyFactory factory = proxyServerSide.read(in);
												//%% Ready r = new QueueReady(queue, factory.create());
												Ready r = factory.create();
												r.connect(address, new ReadyConnection() {
													@Override
													public void failed(IOException e) {
														LOGGER.warn("Could not connect to {}", address, e);
														try {
															out.writeInt(connectionId);
															out.writeInt(-ProxyCommons.Commands.FAIL_CONNECTION);
															out.flush();
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
															if (closed[0]) {
																write.close();
																return;
															}
															establishedConnections.put(connectionId, new Pair<Address, CloseableByteBufferHandler>(address, write));
														}
	
														try {
															out.writeInt(connectionId);
															out.writeInt(-ProxyCommons.Commands.ESTABLISH_CONNECTION);
															out.flush();
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
															out.flush();
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
															if (address == null) {
																out.writeBoolean(false);
															} else {
																out.writeBoolean(true);
																out.writeUTF(address.getHost());
																out.writeInt(address.getPort());
															}
															out.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
															out.flush();
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
											Pair<Address, CloseableByteBufferHandler> connection;
											synchronized (establishedConnections) {
												connection = establishedConnections.get(connectionId);
											}
											Address a;
											if (in.readBoolean()) {
												a = new Address(in.readUTF(), in.readInt());
											} else {
												a = (connection == null) ? null : connection.first;
											}
											byte[] b = new byte[len];
											in.readFully(b);
											if (connection != null) {
												if (!addressesToFilter.contains(a)) {
													if (connection.second != null) {
														connection.second.handle(a, ByteBuffer.wrap(b));
													}
												}
											} else {
												LOGGER.debug("Invalid connection ID: {}", connectionId);
											}
										}
									}
								} catch (IOException e) {
									LOGGER.info("Socket closed by peer: {}", e.getMessage());
	
									try {
										socket.close();
									} catch (IOException ioe) {
									}
									
									List<CloseableByteBufferHandler> toClose = new LinkedList<>();
									synchronized (establishedConnections) {
										closed[0] = true;
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
				} catch (IOException se) {
					LOGGER.debug("Server socket closed: {}", se.getMessage());
				}
			}
		});
		return this;
	}
}
