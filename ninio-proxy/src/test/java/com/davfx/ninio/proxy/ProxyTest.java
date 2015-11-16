package com.davfx.ninio.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.DatagramReady;
import com.davfx.ninio.core.DatagramReadyFactory;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Listen;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.QueueListen;
import com.davfx.ninio.core.QueueReady;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.core.SocketListen;
import com.davfx.ninio.core.SocketListening;
import com.davfx.ninio.core.SocketReadyFactory;
import com.davfx.ninio.ping.Ping;
import com.davfx.ninio.ping.PingClientHandler.Callback.PingCallback;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;

public class ProxyTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProxyTest.class);
	
	@Test
	public void testSocket() throws Exception {
		int proxyServerPort = 9999;
		
		try (Queue queue = new Queue(); ProxyServer proxyServer = new ProxyServer(proxyServerPort, 1)) {
			proxyServer.start();
			try (ProxyClient proxyClient = new ProxyClient(new Address(Address.LOCALHOST, proxyServerPort), new ProxyListener() {
				@Override
				public void failed(IOException e) {
					LOGGER.warn("Proxy failed", e);
				}
				@Override
				public void disconnected() {
					LOGGER.debug("Proxy disconnected");
				}
				@Override
				public void connected() {
					LOGGER.debug("Proxy connected");
				}
			})) {
			
				int port = 7777;
				
				Listen socketListen = new SocketListen(queue.getSelector(), queue.allocator());
				new QueueListen(queue, socketListen).listen(new Address(Address.LOCALHOST, port), new SocketListening() {
					@Override
					public void listening(Closeable closeable) {
					}
					
					@Override
					public CloseableByteBufferHandler connected(Address address, final CloseableByteBufferHandler connection) {
						return new CloseableByteBufferHandler() {
							@Override
							public void close() {
							}
							
							@Override
							public void handle(Address address, ByteBuffer buffer) {
								String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
								connection.handle(address, ByteBuffer.wrap(("echo " + s).getBytes(Charsets.UTF_8)));
							}
						};
					}
					
					@Override
					public void close() {
					}
					@Override
					public void failed(IOException e) {
					}
				});
				
				final Lock<String, IOException> lock = new Lock<>();
				
				ReadyFactory socketReadyFactory = new SocketReadyFactory();
				
				socketReadyFactory = proxyClient.socket();
				
				Ready socketReady = socketReadyFactory.create(queue);
				socketReady.connect(new Address(Address.LOCALHOST, port), new ReadyConnection() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						lock.set(s);
					}
					
					@Override
					public void failed(IOException e) {
						lock.fail(e);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						write.handle(null, ByteBuffer.wrap("hello".getBytes(Charsets.UTF_8)));
					}
					
					@Override
					public void close() {
						lock.fail(new IOException("Closed"));
					}
				});
				
				Assertions.assertThat(lock.waitFor()).isEqualTo("echo hello");
			}
		}
	}
	
	@Test
	public void testDatagram() throws Exception {
		int proxyServerPort = 9999;
		
		try (Queue queue = new Queue(); ProxyServer proxyServer = new ProxyServer(proxyServerPort, 1)) {
			proxyServer.start();
			try (ProxyClient proxyClient = new ProxyClient(new Address(Address.LOCALHOST, proxyServerPort), new ProxyListener() {
				@Override
				public void failed(IOException e) {
					LOGGER.warn("Proxy failed", e);
				}
				@Override
				public void disconnected() {
					LOGGER.debug("Proxy disconnected");
				}
				@Override
				public void connected() {
					LOGGER.debug("Proxy connected");
				}
			})) {
				
				int port = 7777;
				
				new QueueReady(queue, new DatagramReady(queue.getSelector(), queue.allocator()).bind()).connect(new Address(null, port), new ReadyConnection() {
					private FailableCloseableByteBufferHandler write;
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Received from client: {}", s);
						write.handle(address, ByteBuffer.wrap(("echo " + s).getBytes(Charsets.UTF_8)));
					}
					
					@Override
					public void failed(IOException e) {
						LOGGER.error("Failed", e);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						LOGGER.debug("<- Client connected");
						this.write = write;
					}
					
					@Override
					public void close() {
						LOGGER.debug("Server closed");
					}
				});
				
				Thread.sleep(100);
				
				final Lock<String, IOException> lock = new Lock<>();
				
				ReadyFactory socketReadyFactory = new DatagramReadyFactory();
				
				socketReadyFactory = proxyClient.datagram();
				
				Ready socketReady = socketReadyFactory.create(queue);
				socketReady.connect(new Address(Address.LOCALHOST, port), new ReadyConnection() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Received from server: {}", s);
						lock.set(s);
					}
					
					@Override
					public void failed(IOException e) {
						lock.fail(e);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						LOGGER.debug("Client connected ->");
						write.handle(null, ByteBuffer.wrap("hello".getBytes(Charsets.UTF_8)));
					}
					
					@Override
					public void close() {
						lock.fail(new IOException("Closed"));
					}
				});
		
				Assertions.assertThat(lock.waitFor()).isEqualTo("echo hello");
			}
		}
	}

	@Test
	public void testPing() throws Exception {
		int proxyServerPort = 9999;
		
		try (Queue queue = new Queue(); ProxyServer proxyServer = new ProxyServer(proxyServerPort, 1)) {
			proxyServer.start();
			try (ProxyClient proxyClient = new ProxyClient(new Address(Address.LOCALHOST, proxyServerPort), new ProxyListener() {
				@Override
				public void failed(IOException e) {
					LOGGER.warn("Proxy failed", e);
				}
				@Override
				public void disconnected() {
					LOGGER.debug("Proxy disconnected");
				}
				@Override
				public void connected() {
					LOGGER.debug("Proxy connected");
				}
			})) {
				
				final Lock<Double, IOException> lock = new Lock<>();
				
				new Ping().override(proxyClient.ping()).ping("127.0.0.1", new PingCallback() {
					@Override
					public void failed(IOException e) {
						e.printStackTrace();
					}
					@Override
					public void pong(double time) {
						LOGGER.debug("Pong: {}", time);
						lock.set(time);
					}
				});
		
				Assertions.assertThat(lock.waitFor()).isNotNull();
			}
		}
	}
	
	@Test
	public void testReproxy() throws Exception {
		int proxy0ServerPort = 9998;
		int proxy1ServerPort = 9999;
		
		try (Queue queue = new Queue(); ProxyServer proxy0Server = new ProxyServer(proxy0ServerPort, 1)) {
			proxy0Server.start();
			
			try (ProxyServer proxy1Server = new ProxyServer(proxy1ServerPort, 1)) {
				proxy1Server.start();
				
				try (ProxyClient proxyClient = new ProxyClient(new Address(Address.LOCALHOST, proxy1ServerPort), new ProxyListener() {
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Proxy failed", e);
					}
					@Override
					public void disconnected() {
						LOGGER.debug("Proxy disconnected");
					}
					@Override
					public void connected() {
						LOGGER.debug("Proxy connected");
					}
				})) {
				
					proxyClient.hopTo(new Address(Address.LOCALHOST, proxy0ServerPort), ProxyCommons.Types.SOCKET);
					
					int port = 7777;
					
					Listen socketListen = new SocketListen(queue.getSelector(), queue.allocator());
					new QueueListen(queue, socketListen).listen(new Address(Address.LOCALHOST, port), new SocketListening() {
						@Override
						public void listening(Closeable closeable) {
						}
						
						@Override
						public CloseableByteBufferHandler connected(Address address, final CloseableByteBufferHandler connection) {
							return new CloseableByteBufferHandler() {
								@Override
								public void close() {
								}
								
								@Override
								public void handle(Address address, ByteBuffer buffer) {
									String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
									connection.handle(address, ByteBuffer.wrap(("echo " + s).getBytes(Charsets.UTF_8)));
								}
							};
						}
						
						@Override
						public void close() {
						}
						@Override
						public void failed(IOException e) {
						}
					});
					
					final Lock<String, IOException> lock = new Lock<>();
					
					ReadyFactory socketReadyFactory = new SocketReadyFactory();
					
					socketReadyFactory = proxyClient.hop();
					
					Ready socketReady = socketReadyFactory.create(queue);
					socketReady.connect(new Address(Address.LOCALHOST, port), new ReadyConnection() {
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
							lock.set(s);
						}
						
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
						
						@Override
						public void connected(FailableCloseableByteBufferHandler write) {
							write.handle(null, ByteBuffer.wrap("hello".getBytes(Charsets.UTF_8)));
						}
						
						@Override
						public void close() {
							lock.fail(new IOException("Closed"));
						}
					});
					
					Assertions.assertThat(lock.waitFor()).isEqualTo("echo hello");
				}
			}
		}
	}
	
	@Test
	public void testForward() throws Exception {
		int proxy0ServerPort = 9998;
		int proxy1ServerPort = 9999;
		
		try (Queue queue = new Queue(); ProxyServer proxy0Server = new ProxyServer(proxy0ServerPort, 1)) {
			proxy0Server.start();
			
			try (ProxyServer proxy1Server = new ProxyServer(proxy1ServerPort, 1)) {
				proxy1Server.override(ProxyCommons.Types.SOCKET, new ForwardServerSideConfigurator(new Address(Address.LOCALHOST, proxy0ServerPort), new ProxyListener() {
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Forward failed", e);
					}
					@Override
					public void disconnected() {
						LOGGER.debug("Forward disconnected");
					}
					@Override
					public void connected() {
						LOGGER.debug("Forward connected");
					}
				}));
				proxy1Server.start();
				
				try (ProxyClient proxyClient = new ProxyClient(new Address(Address.LOCALHOST, proxy1ServerPort), new ProxyListener() {
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Proxy failed", e);
					}
					@Override
					public void disconnected() {
						LOGGER.debug("Proxy disconnected");
					}
					@Override
					public void connected() {
						LOGGER.debug("Proxy connected");
					}
				})) {
				
					int port = 7777;
					
					Listen socketListen = new SocketListen(queue.getSelector(), queue.allocator());
					new QueueListen(queue, socketListen).listen(new Address(Address.LOCALHOST, port), new SocketListening() {
						@Override
						public void listening(Closeable closeable) {
						}
						
						@Override
						public CloseableByteBufferHandler connected(Address address, final CloseableByteBufferHandler connection) {
							return new CloseableByteBufferHandler() {
								@Override
								public void close() {
								}
								
								@Override
								public void handle(Address address, ByteBuffer buffer) {
									String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
									connection.handle(address, ByteBuffer.wrap(("echo " + s).getBytes(Charsets.UTF_8)));
								}
							};
						}
						
						@Override
						public void close() {
						}
						@Override
						public void failed(IOException e) {
						}
					});
					
					final Lock<String, IOException> lock = new Lock<>();
					
					ReadyFactory socketReadyFactory = new SocketReadyFactory();
					
					socketReadyFactory = proxyClient.socket();
					
					Ready socketReady = socketReadyFactory.create(queue);
					socketReady.connect(new Address(Address.LOCALHOST, port), new ReadyConnection() {
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
							lock.set(s);
						}
						
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
						
						@Override
						public void connected(FailableCloseableByteBufferHandler write) {
							write.handle(null, ByteBuffer.wrap("hello".getBytes(Charsets.UTF_8)));
						}
						
						@Override
						public void close() {
							lock.fail(new IOException("Closed"));
						}
					});
					
					Assertions.assertThat(lock.waitFor()).isEqualTo("echo hello");
				}
			}
		}
	}
	
}
