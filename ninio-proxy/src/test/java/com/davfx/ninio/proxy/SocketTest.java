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
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.LockFailedConnecterCallback;
import com.davfx.ninio.core.LockFailedConnecterConnectingCallback;
import com.davfx.ninio.core.LockFailedListenerCallback;
import com.davfx.ninio.core.LockReceivedConnecterCallback;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.NopConnecterCallback;
import com.davfx.ninio.core.NopConnecterConnectingCallback;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.WaitClosedConnecterCallback;
import com.davfx.ninio.core.WaitClosedListenerCallback;
import com.davfx.ninio.core.WaitConnectedConnecterCallback;
import com.davfx.ninio.core.WaitConnectedListenerCallback;
import com.davfx.ninio.core.WaitSentConnecterConnectingCallback;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class SocketTest {

	@Test
	public void test() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
	
			Wait serverWaitConnecting = new Wait();
			Wait serverWaitClosing = new Wait();
			final Wait serverWaitClientConnecting = new Wait();
			final Wait serverWaitClientClosing = new Wait();
			try (Listener server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
				server.listen(
					new WaitConnectedListenerCallback(serverWaitConnecting,
					new WaitClosedListenerCallback(serverWaitClosing,
					new LockFailedListenerCallback(lock,
					new Listener.Callback() {
						@Override
						public void failed(IOException ioe) {
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void closed() {
						}
						
						@Override
						public Connection connecting(final Connected connecting) {
							return new Connection() {
								@Override
								public void received(Address address, ByteBuffer buffer) {
									connecting.send(null, buffer, new NopConnecterConnectingCallback());
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
					}))));

				serverWaitConnecting.waitFor();

				Wait serverWaitForProxyServerClosing = new Wait();

				int proxyPort = 8081;

				Wait clientWaitConnecting = new Wait();
				Wait clientWaitClosing = new Wait();
				Wait clientWaitSent = new Wait();

				try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new WaitProxyListening(serverWaitForProxyServerClosing)))) {
					try (ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
						try (Connecter client = ninio.create(proxyClient.tcp().to(new Address(Address.LOCALHOST, port)))) {
							client.connect(
								new WaitConnectedConnecterCallback(clientWaitConnecting, 
								new WaitClosedConnecterCallback(clientWaitClosing, 
								new LockFailedConnecterCallback(lock, 
								new LockReceivedConnecterCallback(lock,
								new NopConnecterCallback())))));
							client.send(null, ByteBufferUtils.toByteBuffer("test"),
								new WaitSentConnecterConnectingCallback(clientWaitSent,
								new LockFailedConnecterConnectingCallback(lock,
								new NopConnecterConnectingCallback())));
							
							clientWaitConnecting.waitFor();
							serverWaitClientConnecting.waitFor();
							Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test");
						}
		
						clientWaitClosing.waitFor();
						serverWaitClientClosing.waitFor();
					}
				}
			}
			serverWaitClosing.waitFor();
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
}
