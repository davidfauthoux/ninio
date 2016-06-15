package com.davfx.ninio.proxy.v3;

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
import com.davfx.ninio.http.HttpContentSender;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.HttpListeningHandler;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.WebsocketHttpContentReceiver;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.proxy.ProxyConnectorProvider;
import com.davfx.ninio.proxy.ProxyServer;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;

public class HttpSocketTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpSocketTest.class);
	
	@Test
	public void testSocket() throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			try {
				
				final int port = 8080;

				Disconnectable websocketServer = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)).listening(HttpListening.builder().with(Executors.newSingleThreadExecutor()).with(new HttpListeningHandler() {
					@Override
					public ConnectionHandler create() {
						return new ConnectionHandler() {
							@Override
							public HttpContentReceiver handle(HttpRequest request, ResponseHandler responseHandler) {
								LOGGER.debug("----> {}", request);
								if (request.path.equals("/ws")) {
									final HttpContentSender sender = responseHandler.send(HttpResponse.ok());
									return new HttpContentReceiver() {
										@Override
										public void ended() {
											LOGGER.debug("Socket closed <--");
										}
										@Override
										public void received(ByteBuffer buffer) {
											String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
											LOGGER.debug("Received <--: {}", s);
											sender.send(ByteBuffer.wrap(("" + s).getBytes(Charsets.UTF_8)));
										}
									};
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
							final Connector client = ninio.create(proxyClient.http().route("/ws").to(new Address(Address.LOCALHOST, port))
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
										if (b.length() == 10) {
											lock.set(b.toString());
										}
									}
								})
								.connecting(new Connecting() {
									@Override
									public void connected(Address to, Connector connector) {
										LOGGER.debug("Client socket connected {} <--", to);
									}
								})
							);
							try {
								client.send(null, ByteBuffer.wrap("test0".getBytes(Charsets.UTF_8)));
								client.send(null, ByteBuffer.wrap("test1".getBytes(Charsets.UTF_8)));
			
								Assertions.assertThat(lock.waitFor()).isEqualTo("test0test1");
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
