package com.davfx.ninio.core.v4;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

public final class TcpSocketServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(TcpSocketServer.class);

	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(com.davfx.ninio.core.TcpSocketServer.class.getPackage().getName());
	private static final double SOCKET_TIMEOUT = ConfigUtils.getDuration(CONFIG, "tcp.serversocket.timeout");
	private static final long SOCKET_READ_BUFFER_SIZE = CONFIG.getBytes("tcp.serversocket.read").longValue();

	private final Queue queue;
	
	private Address bindAddress = null;
	
	private boolean closed = false;
	private ServerSocketChannel currentServerChannel = null;
	private SelectionKey currentAcceptSelectionKey = null;
	
	private final Deque<CompletableFuture<TcpSocket>> acceptings = new LinkedList<>();
	// private final Deque<CompletableFuture<Void>> onCloses = new LinkedList<>();

	private final Set<SocketChannel> outboundChannels = new HashSet<>();
	
	public TcpSocketServer(Ninio ninio) {
		queue = ninio.register(NinioPriority.REGULAR);
	}
	
	/*
	public CompletableFuture<Void> onClose() {
		CompletableFuture<Void> future = new CompletableFuture<>();
		queue.execute(new Runnable() {
			@Override
			public void run() {
				if (closed) {
					future.complete(null);
					return;
				}
				onCloses.addLast(future);
			}
		});
		return future;
	}
	*/
	
	public TcpSocketServer bind(Address bindAddress) {
		this.bindAddress = bindAddress;
		return this;
	}
	
	public CompletableFuture<Void> listen() {
		CompletableFuture<Void> future = new CompletableFuture<>();

		queue.execute(new Runnable() {
			@Override
			public void run() {
				try {
					if (closed) {
						throw new IOException("Closed");
					}
					if (currentServerChannel != null) {
						throw new IllegalStateException("listen() cannot be called twice");
					}

					final ServerSocketChannel serverChannel = ServerSocketChannel.open();
					currentServerChannel = serverChannel;
					try {
						serverChannel.configureBlocking(false);
						if (SOCKET_TIMEOUT > 0d) {
							serverChannel.socket().setSoTimeout((int) (SOCKET_TIMEOUT * 1000d)); // Not working with NIO?
						}
						if (SOCKET_READ_BUFFER_SIZE > 0L) {
							serverChannel.socket().setReceiveBufferSize((int) SOCKET_READ_BUFFER_SIZE);
						}

						LOGGER.debug("-> Server channel ready to accept on: {}", bindAddress);

						final SelectionKey acceptSelectionKey = queue.register(serverChannel);
						currentAcceptSelectionKey = acceptSelectionKey;
						
						acceptSelectionKey.attach(new SelectionKeyVisitor() {
							@Override
							public void visit(SelectionKey key) {
								if (closed) {
									disconnect(serverChannel, acceptSelectionKey, null);
									return;
								}
								
								if (!key.isAcceptable()) {
									return;
								}
								
								ServerSocketChannel innerServerChannel = (ServerSocketChannel) key.channel();
								try {
									LOGGER.debug("-> Accepting client on: {}", bindAddress);
									final SocketChannel outboundChannel = innerServerChannel.accept();
									
									outboundChannels.add(outboundChannel);
									
									if (acceptings.isEmpty()) {
										throw new IOException("Invalid acception");
									}
									TcpSocket s = new TcpSocket(new Queue() {
										@Override
										public void close() {
											outboundChannels.remove(outboundChannel);
											 // No call to underlying queue
										}
										@Override
										public SelectionKey register(SelectableChannel channel) throws ClosedChannelException {
											return queue.register(channel);
										}
										@Override
										public void execute(Runnable command) {
											queue.execute(command);
										}
									}, new SocketChannelProvider() {
										@Override
										public SocketChannel open() throws IOException {
											return outboundChannel;
										}
									});
									s.connect(null);
									acceptings.removeLast().complete(s);
								} catch (IOException e) {
									disconnect(serverChannel, acceptSelectionKey, e);
									LOGGER.error("Error while accepting on: {}", bindAddress, e);
								}
							}
						});

						try {
							InetSocketAddress a = new InetSocketAddress(InetAddress.getByAddress(bindAddress.ip), bindAddress.port);
							LOGGER.debug("-> Bound on: {}", a);
							serverChannel.socket().bind(a);
						} catch (IOException e) {
							disconnect(serverChannel, acceptSelectionKey, e);
							throw new IOException("Could not bind to: " + bindAddress, e);
						}

						future.complete(null);
					} catch (IOException ee) {
						disconnect(serverChannel, null, ee);
						throw new IOException("Error while creating server socket on: " + bindAddress, ee);
					}
				} catch (IOException eee) {
					disconnect(null, null, eee);
					future.completeExceptionally(new IOException("Error while creating server socket on: " + bindAddress, eee));
				}
			}
		});
		
		return future;
	}
	
	private void disconnect(ServerSocketChannel serverChannel, SelectionKey acceptSelectionKey, IOException error) {
		if (serverChannel != null) {
			try {
				serverChannel.close();
			} catch (IOException e) {
			}
			LOGGER.debug("Server channel closed, bindAddress = {}", bindAddress);
		}
		if (acceptSelectionKey != null) {
			acceptSelectionKey.cancel();
		}
		
		for (SocketChannel context : outboundChannels) {
			LOGGER.debug("Closing outbound channel");
			try {
				context.close();
			} catch (IOException e) {
			}
		}
		outboundChannels.clear();

		currentServerChannel = null;
		currentAcceptSelectionKey = null;
		
		if (!closed) {
			closed = true;
	
			/*
			for (CompletableFuture<Void> onClose : onCloses) {
				onClose.complete(null);
			}
			*/

			queue.close();
		}

	}

	public CompletableFuture<TcpSocket> accept() {
		CompletableFuture<TcpSocket> future = new CompletableFuture<>();

		queue.execute(new Runnable() {
			@Override
			public void run() {
				if (currentAcceptSelectionKey == null) {
					future.completeExceptionally(new IOException("Closed"));
					return;
				}
				acceptings.addLast(future);
				currentAcceptSelectionKey.interestOps(currentAcceptSelectionKey.interestOps() | SelectionKey.OP_ACCEPT);
			}
		});
		
		return future;
	}

	public void close() {
		queue.execute(new Runnable() {
			@Override
			public void run() {
				disconnect(currentServerChannel, currentAcceptSelectionKey, null);
			}
		});
	}
}
