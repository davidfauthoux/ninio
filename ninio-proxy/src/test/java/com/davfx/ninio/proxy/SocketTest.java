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
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.LockFailedConnection;
import com.davfx.ninio.core.LockListening;
import com.davfx.ninio.core.LockReceivedConnection;
import com.davfx.ninio.core.LockSendCallback;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.WaitClosedConnection;
import com.davfx.ninio.core.WaitClosedListening;
import com.davfx.ninio.core.WaitConnectedConnection;
import com.davfx.ninio.core.WaitConnectedListening;
import com.davfx.ninio.core.WaitSentSendCallback;
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
					new WaitConnectedListening(serverWaitConnecting,
					new WaitClosedListening(serverWaitClosing,
					new LockListening(lock,
					new Listening() {
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
									connecting.send(null, buffer, new Nop());
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

				try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new WaitProxyListening(serverWaitForProxyServerClosing)))) {
					try (ProxyProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
						Wait clientWaitClosing = new Wait();
						try (Connecter client = ninio.create(proxyClient.tcp().to(new Address(Address.LOCALHOST, port)))) {
							Wait clientWaitConnecting = new Wait();
							client.connect(
								new WaitConnectedConnection(clientWaitConnecting, 
								new WaitClosedConnection(clientWaitClosing, 
								new LockFailedConnection(lock, 
								new LockReceivedConnection(lock,
								new Nop())))));
							Wait clientWaitSent = new Wait();
							client.send(null, ByteBufferUtils.toByteBuffer("test"),
								new WaitSentSendCallback(clientWaitSent,
								new LockSendCallback(lock,
								new Nop())));
							
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
