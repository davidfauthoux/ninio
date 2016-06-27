package com.davfx.ninio.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Receiver;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.HttpListeningHandler;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.WebsocketHttpContentReceiver;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.proxy.ProxyConnectorProvider;
import com.davfx.ninio.proxy.ProxyServer;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;
import com.google.common.base.Charsets;

public class WebsocketSocketTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketSocketTest.class);
	
	@Test
	public void testSocket() throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			try {
				
				final int port = 8080;

				Disconnectable websocketServer = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)).listening(HttpListening.builder().with(new SerialExecutor(WebsocketSocketTest.class)).with(new HttpListeningHandler() {
					@Override
					public ConnectionHandler create() {
						return new ConnectionHandler() {
							@Override
							public HttpContentReceiver handle(HttpRequest request, ResponseHandler responseHandler) {
								LOGGER.debug("----> {}", request);
								if (request.path.equals("/ws")) {
									return new WebsocketHttpContentReceiver(request, responseHandler, false, new Listening() {
										@Override
										public Connection connecting(Address from, Connector connector) {
											return new Connection() {
												public Failing failing() {
													return new Failing() {
														@Override
														public void failed(IOException e) {
															LOGGER.warn("Socket failed <--", e);
														}
													};
												}
												public Connecting connecting() {
													return new Connecting() {
														@Override
														public void connected(Connector conn, Address address) {
															LOGGER.debug("Socket connected {} <--", address);
														}
													};
												}
												public Closing closing() {
													return new Closing() {
														@Override
														public void closed() {
															LOGGER.debug("Socket closed <--");
														}
													};
												}
												public Receiver receiver() {
													return new Receiver() {
														@Override
														public void received(Connector connector, Address address, ByteBuffer buffer) {
															String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
															LOGGER.debug("Received {} <--: {}", address, s);
															connector.send(null, ByteBuffer.wrap(("ECHO " + s).getBytes(Charsets.UTF_8)));
														}
													};
												}
											};
										}
									});
								} else {
									responseHandler.send(HttpResponse.notFound()).finish();
									return null;
								}
							}
							@Override
							public void closed() {
							}
						};
					}
				}).build()));
				try {
					final int proxyPort = 8081;
	
					final Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), null));
					try {
				
						final ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)));
						try {
							final Connector client = ninio.create(proxyClient.websocket().route("/ws").to(new Address(Address.LOCALHOST, port))
								.failing(new Failing() {
									@Override
									public void failed(IOException e) {
										LOGGER.warn("Failed <--", e);
										lock.fail(e);
									}
								})
								.closing(new Closing() {
									@Override
									public void closed() {
										LOGGER.debug("Closed <--");
										lock.fail(new IOException("Closed"));
									}
								})
								.receiving(new Receiver() {
									private final StringBuilder b = new StringBuilder();
									@Override
									public void received(Connector c, Address address, ByteBuffer buffer) {
										String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
										b.append(s);
										LOGGER.info("Received {} -->: {} ({})", address, s, b);
										if (b.length() == 20) {
											lock.set(b.toString());
										}
									}
								})
								.connecting(new Connecting() {
									@Override
									public void connected(Connector conn, Address address) {
										LOGGER.debug("Client socket connected {} <--", address);
									}
								})
							);
							try {
								client.send(null, ByteBuffer.wrap("test0".getBytes(Charsets.UTF_8)));
								client.send(null, ByteBuffer.wrap("test1".getBytes(Charsets.UTF_8)));
			
								Assertions.assertThat(lock.waitFor()).isEqualTo("ECHO test0ECHO test1");
							} finally {
								client.close();
							}
						} finally {
							proxyClient.close();
						}
					} finally {
						proxyServer.close();
					}
				} finally {
					websocketServer.close();
				}
			} finally {
				executor.shutdown();
			}
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
	
}
