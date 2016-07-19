package com.davfx.ninio.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Connected;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.LockFailedConnecterCallback;
import com.davfx.ninio.core.LockReceivedConnecterCallback;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.NopConnecterCallback;
import com.davfx.ninio.core.NopConnecterConnectingCallback;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.core.WaitClosedConnecterCallback;
import com.davfx.ninio.core.WaitConnectedConnecterCallback;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.HttpListeningHandler;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.WebsocketHttpContentReceiver;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;

public class WebsocketSocketTest {

	@Test
	public void test() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		final Wait serverWaitServerConnecting = new Wait();
		final Wait serverWaitServerClosing = new Wait();
		final Wait serverWaitClientConnecting = new Wait();
		final Wait serverWaitClientClosing = new Wait();

		int port = 8080;
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (Listener tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
				tcp.listen(HttpListening.builder().with(new SerialExecutor(WebsocketSocketTest.class)).with(new HttpListeningHandler() {
					@Override
					public HttpContentReceiver handle(HttpRequest request, final ResponseHandler responseHandler) {
						return new WebsocketHttpContentReceiver(request, responseHandler, true, new Listener.Callback() { //TODO WebsocketHttpContentReceiver extends HttpListeningHandler
							@Override
							public void closed() {
							}
							@Override
							public void failed(IOException e) {
								lock.fail(e);
							}
							@Override
							public void connected(Address address) {
							}
							
							@Override
							public Connection connecting(final Connected connecting) {
								return new Connection() {
									private final InMemoryBuffers buffers = new InMemoryBuffers();
									@Override
									public void received(Address address, ByteBuffer buffer) {
										buffers.add(buffer);
										String s = buffers.toString();
										if (s.indexOf('\n') >= 0) {
											connecting.send(null, ByteBufferUtils.toByteBuffer("ECHO " + s), new NopConnecterConnectingCallback());
										}
									}
									
									@Override
									public void failed(IOException ioe) {
										lock.fail(ioe);
									}
									@Override
									public void connected(Address address) {
										serverWaitClientConnecting.run();
									}
									@Override
									public void closed() {
										serverWaitClientClosing.run();
									}
								};
							}
						});
				}
				@Override
				public void closed() {
					serverWaitServerClosing.run();
				}
				@Override
				public void connected() {
					serverWaitServerConnecting.run();
				}
				@Override
				public void failed(IOException ioe) {
					lock.fail(ioe);
				}
			}).build());
				
				serverWaitServerConnecting.waitFor();
	
				Wait serverWaitForProxyServerClosing = new Wait();

				int proxyPort = 8081;

				Wait clientWaitConnecting = new Wait();
				Wait clientWaitClosing = new Wait();
				final Wait clientWaitSending0 = new Wait();
				final Wait clientWaitSending1 = new Wait();

				try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new WaitProxyListening(serverWaitForProxyServerClosing)))) {
					try (ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
						try (HttpClient httpClient = ninio.create(HttpClient.builder().with(new SerialExecutor(WebsocketSocketTest.class)))) {
							try (Connecter client = ninio.create(proxyClient.websocket().route("/ws").to(new Address(Address.LOCALHOST, port)))) {
								client.connect(
										new WaitConnectedConnecterCallback(clientWaitConnecting, 
										new WaitClosedConnecterCallback(clientWaitClosing, 
										new LockFailedConnecterCallback(lock, 
										new LockReceivedConnecterCallback(lock,
										new NopConnecterCallback())))));
								
								clientWaitConnecting.waitFor();
								client.send(null, ByteBufferUtils.toByteBuffer("test0"), new SendCallback() {
									@Override
									public void failed(IOException e) {
										lock.fail(e);
									}
									@Override
									public void sent() {
										clientWaitSending0.run();
									}
								});
								client.send(null, ByteBufferUtils.toByteBuffer("test1\n"), new SendCallback() {
									@Override
									public void failed(IOException e) {
										lock.fail(e);
									}
									@Override
									public void sent() {
										clientWaitSending1.run();
									}
								});
								clientWaitSending0.waitFor();
								clientWaitSending1.waitFor();
								Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("ECHO test0test1\n");
							}
							clientWaitClosing.waitFor();
						}
					}
				}

				serverWaitForProxyServerClosing.waitFor();
			}
			serverWaitServerClosing.waitFor();
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
	
}
