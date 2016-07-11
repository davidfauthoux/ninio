package com.davfx.ninio.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.LockFailing;
import com.davfx.ninio.core.LockReceiver;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.WaitClosing;
import com.davfx.ninio.core.WaitConnecting;
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
	public void testSocket() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;

			final Wait waitForServerClosing = new Wait();
			try (Disconnectable httpSocketServer = ninio.create(TcpSocketServer.builder()
					.closing(new WaitClosing(waitForServerClosing))
					.bind(new Address(Address.ANY, port)).listening(HttpListening.builder().with(new SerialExecutor(HttpSocketTest.class)).with(new HttpListeningHandler() {
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
										String s = ByteBufferUtils.toString(buffer);
										LOGGER.debug("Received <--: {}", s);
										sender.send(ByteBufferUtils.toByteBuffer(s));
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
						@Override
						public void buffering(long size) {
						}
					};
				}
			}).build()))) {
				int proxyPort = 8081;

				Wait wait = new Wait();
				Wait waitForProxyServerClosing = new Wait();
				Wait waitForClientClosing = new Wait();
				try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new WaitProxyListening(waitForProxyServerClosing)))) {
					try (ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
						try (Connector client = ninio.create(proxyClient.http().route("/ws").to(new Address(Address.LOCALHOST, port))
							.failing(new LockFailing(lock))
							.closing(new WaitClosing(waitForClientClosing))
							//.closing(new LockClosing(lock))
							.receiving(new LockReceiver(lock))
							.connecting(new WaitConnecting(wait))
						)) {
							wait.waitFor();
							client.send(null, ByteBufferUtils.toByteBuffer("test0"));
							Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test0");
						}
					}
					waitForClientClosing.waitFor();
				}
				waitForProxyServerClosing.waitFor();
			}
			waitForServerClosing.waitFor();
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
	
}
