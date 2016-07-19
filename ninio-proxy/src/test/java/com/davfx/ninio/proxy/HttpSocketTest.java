package com.davfx.ninio.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.LockFailedConnecterCallback;
import com.davfx.ninio.core.LockFailedConnecterConnectingCallback;
import com.davfx.ninio.core.LockReceivedConnecterCallback;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.NopConnecterCallback;
import com.davfx.ninio.core.NopConnecterConnectingCallback;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.WaitClosedConnecterCallback;
import com.davfx.ninio.core.WaitConnectedConnecterCallback;
import com.davfx.ninio.core.WaitSentConnecterConnectingCallback;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpContentSender;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.HttpListeningHandler;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;

public class HttpSocketTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpSocketTest.class);
	
	@Test
	public void test() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;

			final Wait serverWaitHttpServerClosing = new Wait();
			final Wait serverWaitHttpServerConnecting = new Wait();
			try (Listener httpSocketServer = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
				httpSocketServer.listen(HttpListening.builder().with(new SerialExecutor(HttpSocketTest.class)).with(new HttpListeningHandler() {
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
								String s = ByteBufferUtils.toString(buffer);
								LOGGER.debug("Received <--: {}", s);
								sender.send(ByteBufferUtils.toByteBuffer("ECHO " + s), new NopConnecterConnectingCallback());
							}
						};
					} else {
						responseHandler.send(HttpResponse.notFound()).finish();
						return null;
					}
				}
				@Override
				public void closed() {
					serverWaitHttpServerClosing.run();
				}
				@Override
				public void connected() {
					serverWaitHttpServerConnecting.run();
				}
				@Override
				public void failed(IOException ioe) {
					lock.fail(ioe);
				}
			}).build());
				
				serverWaitHttpServerConnecting.waitFor();
				
				int proxyPort = 8081;

				Wait serverWaitForProxyServerClosing = new Wait();

				Wait clientWaitConnecting = new Wait();
				Wait clientWaitClosing = new Wait();
				Wait clientWaitSent = new Wait();

				try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new WaitProxyListening(serverWaitForProxyServerClosing)))) {
					try (ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
						try (Connecter client = ninio.create(proxyClient.http().route("/ws").to(new Address(Address.LOCALHOST, port)))) {
							client.connect(
									new WaitConnectedConnecterCallback(clientWaitConnecting, 
									new WaitClosedConnecterCallback(clientWaitClosing, 
									new LockFailedConnecterCallback(lock, 
									new LockReceivedConnecterCallback(lock,
									new NopConnecterCallback())))));
								
							client.send(null, ByteBufferUtils.toByteBuffer("test0"),
									new WaitSentConnecterConnectingCallback(clientWaitSent,
									new LockFailedConnecterConnectingCallback(lock,
									new NopConnecterConnectingCallback())));
								
							clientWaitConnecting.waitFor();
							Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("ECHO test0");
						}
					}
					clientWaitClosing.waitFor();
				}

				serverWaitForProxyServerClosing.waitFor();
			}
			
			serverWaitHttpServerClosing.waitFor();
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
	
	@Test
	public void testDirectlyReceiving() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;

			final Wait serverWaitHttpServerClosing = new Wait();
			final Wait serverWaitHttpServerConnecting = new Wait();
			try (Listener httpSocketServer = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
				httpSocketServer.listen(HttpListening.builder().with(new SerialExecutor(HttpSocketTest.class)).with(new HttpListeningHandler() {
				@Override
				public HttpContentReceiver handle(HttpRequest request, ResponseHandler responseHandler) {
					LOGGER.debug("----> {}", request);
					if (request.path.equals("/ws")) {
						final HttpContentSender sender = responseHandler.send(HttpResponse.ok());
						sender.send(ByteBufferUtils.toByteBuffer("test1"), new NopConnecterConnectingCallback());
						return new HttpContentReceiver() {
							@Override
							public void ended() {
								LOGGER.debug("Socket closed <--");
							}
							@Override
							public void received(ByteBuffer buffer) {
								String s = ByteBufferUtils.toString(buffer);
								LOGGER.debug("Received <--: {}", s);
							}
						};
					} else {
						responseHandler.send(HttpResponse.notFound()).finish();
						return null;
					}
				}
				@Override
				public void closed() {
					serverWaitHttpServerClosing.run();
				}
				@Override
				public void connected() {
					serverWaitHttpServerConnecting.run();
				}
				@Override
				public void failed(IOException ioe) {
					lock.fail(ioe);
				}
			}).build());
				
				serverWaitHttpServerConnecting.waitFor();
				
				int proxyPort = 8081;

				Wait serverWaitForProxyServerClosing = new Wait();

				Wait clientWaitConnecting = new Wait();
				Wait clientWaitClosing = new Wait();

				try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new WaitProxyListening(serverWaitForProxyServerClosing)))) {
					try (ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
						try (Connecter client = ninio.create(proxyClient.http().route("/ws").to(new Address(Address.LOCALHOST, port)))) {
							client.connect(
									new WaitConnectedConnecterCallback(clientWaitConnecting, 
									new WaitClosedConnecterCallback(clientWaitClosing, 
									new LockFailedConnecterCallback(lock, 
									new LockReceivedConnecterCallback(lock,
									new NopConnecterCallback())))));
							
							clientWaitConnecting.waitFor();
							Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test1");
						}
						clientWaitClosing.waitFor();
					}
				}

				serverWaitForProxyServerClosing.waitFor();
			}
			
			serverWaitHttpServerClosing.waitFor();
		}
	}
	
	@Test
	public void testDirectlyReceivingSameToCheckClose() throws Exception {
		testDirectlyReceiving();
	}
}
