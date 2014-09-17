package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;
import com.davfx.ninio.common.OnceByteBufferAllocator;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Ready;
import com.davfx.ninio.common.ReadyConnection;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.Threads;
import com.davfx.util.MutablePair;
import com.davfx.util.WaitUtils;
import com.typesafe.config.ConfigFactory;

public final class ProxyServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyServer.class);

	public static void main(String[] args) throws Exception {
		new ProxyServer(ConfigFactory.load().getInt("proxy.port")).start();
	}
	
	private static final double WAIT_TIME_ON_ERROR = 5d;

	private final int port;
	
	private final ProxyUtils.ServerSide proxyUtils = ProxyUtils.server();
	
	public ProxyServer(int port) {
		this.port = port;
	}

	public ProxyServer override(String type, ProxyUtils.ServerSideConfigurator configurator) {
		proxyUtils.override(type, configurator);
		return this;
	}
	
	private static final class Lock {
		private boolean done = false;
		public Lock() {
		}
		public synchronized void signal() {
			done = true;
			notifyAll();
		}
		public synchronized void await() {
			while (true) {
				if (done) {
					return;
				}
				try {
					wait();
				} catch (InterruptedException e) {
				}
			}
		}
	}

	public void start() throws IOException {
		Queue queue = new Queue();
		Executors.newSingleThreadExecutor().execute(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try (ServerSocket ss = new ServerSocket(port)) {
						while (true) {
							Socket socket = ss.accept();
							
							Threads.run(ProxyServer.class, new Runnable() {
								@Override
								public void run() {
									Map<Integer, MutablePair<Address, CloseableByteBufferHandler>> connections = new HashMap<>();
		
									try {
										DataOutputStream out = new DataOutputStream(socket.getOutputStream());
										DataInputStream in = new DataInputStream(socket.getInputStream());
										LOGGER.debug("Accepted connection from: {}", socket.getInetAddress());
										
										while (true) {
											LOGGER.trace("Server waiting for connection ID");
											int connectionId = in.readInt();
											int len = in.readShort();
											if (len < 0) {
												MutablePair<Address, CloseableByteBufferHandler> connection = connections.remove(connectionId);
												if (connection != null) {
													if (connection.second != null) {
														connection.second.close();
													}
												}
											} else if (len == 0) {
												Address address = new Address(in.readUTF(), in.readInt());
												ReadyFactory factory = proxyUtils.read(in);
												Ready r = factory.create(queue, new OnceByteBufferAllocator());
												MutablePair<Address, CloseableByteBufferHandler> connection = new MutablePair<Address, CloseableByteBufferHandler>(address, null);
												connections.put(connectionId, connection);
												
												Lock lock = new Lock();
												
												r.connect(address, new ReadyConnection() {
													@Override
													public void failed(IOException e) {
														lock.signal();
														try {
															out.writeInt(connectionId);
															out.writeInt(-1);
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
															out.write(buffer.array(), buffer.arrayOffset(), buffer.remaining()); // Should always work
															out.flush();
														} catch (IOException ioe) {
															try {
																out.close();
															} catch (IOException se) {
															}
														}
														buffer.position(buffer.position() + buffer.remaining());
													}
													
													@Override
													public void connected(FailableCloseableByteBufferHandler write) {
														connection.second = write;
														lock.signal();
													}
												});
												
												lock.await();
											} else {
												byte[] b = new byte[len];
												in.readFully(b);
												MutablePair<Address, CloseableByteBufferHandler> connection = connections.get(connectionId);
												if (connection != null) {
													if (connection.second != null) {
														connection.second.handle(null, ByteBuffer.wrap(b));
													}
												}
											}
										}
									} catch (IOException e) {
										try {
											socket.close();
										} catch (IOException ioe) {
										}
										LOGGER.info("Socket closed by peer");
										
										for (MutablePair<Address, CloseableByteBufferHandler> connection : connections.values()) {
											if (connection.second != null) {
												connection.second.close();
											}
										}
									}
								}
							});
						}
					} catch (IOException ioe) {
						LOGGER.error("Could not open server socket", ioe.getMessage()); // We don't want the stacktrace here because it's usually a port-already-in-use problem
						WaitUtils.wait(WAIT_TIME_ON_ERROR);
					}
				}
			}
		});
	}
}
