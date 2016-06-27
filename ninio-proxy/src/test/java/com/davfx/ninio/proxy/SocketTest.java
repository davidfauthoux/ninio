package com.davfx.ninio.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.EchoReceiver;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.LockClosing;
import com.davfx.ninio.core.LockFailing;
import com.davfx.ninio.core.LockReceiver;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Receiver;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.WaitConnecting;
import com.davfx.ninio.core.WaitListenConnecting;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.proxy.ProxyConnectorProvider;
import com.davfx.ninio.proxy.ProxyServer;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Charsets;

public class SocketTest {

	@Test
	public void testSocket() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int proxyPort = 8081;

			try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), null))) {
				
				int port = 8080;
		
				final Wait serverWait = new Wait();
				final Wait clientServerWait = new Wait();
				final Wait clientClientWait = new Wait();
				try (Disconnectable server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port))
					.failing(new LockFailing(lock))
					.connecting(new WaitListenConnecting(serverWait))
					.listening(new Listening() {
						@Override
						public Connection connecting(Address from, Connector connector) {
							return new Connection() {
								public Failing failing() {
									return new LockFailing(lock);
								}
								@Override
								public Closing closing() {
									return new LockClosing(lock);
								}
								@Override
								public Connecting connecting() {
									return new WaitConnecting(clientServerWait);
								}
								@Override
								public Receiver receiver() {
									return new EchoReceiver();
								}
							};
						}
					}))) {
					serverWait.waitFor();
	
					try (ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)));) {
						try (Connector client = ninio.create(proxyClient.tcp().to(new Address(Address.LOCALHOST, port))
							.failing(new LockFailing(lock))
							.closing(new LockClosing(lock))
							.receiving(new LockReceiver(lock))
							.connecting(new WaitConnecting(clientClientWait))
						)) {
							client.send(null, ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));
		
							Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test");
						}
					}
				}
			}
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
	
}
