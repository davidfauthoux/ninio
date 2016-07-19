package com.davfx.ninio.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.NopConnecterConnectingCallback;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Charsets;

public class EchoTest {
	
	@Test
	public void test() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int proxyPort = 8081;

			final Wait serverWaitServerClosing = new Wait();
			final Wait serverWaitServerConnecting = new Wait();
			try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new ProxyListening() {
				@Override
				public void closed() {
					serverWaitServerClosing.run();
				}
				@Override
				public void connected(Address address) {
					serverWaitServerConnecting.run();
				}
				@Override
				public void failed(IOException e) {
					lock.fail(e);
				}
				@Override
				public NinioBuilder<Connecter> create(Address address, String header) {
					if (header.equals("_")) {
						return new EchoNinioSocketBuilder();
					} else {
						return null;
					}
				}
			}))) {
				int port = 8080;
				
				serverWaitServerConnecting.waitFor();
				
				final Wait clientWaitClientConnecting = new Wait();
				final Wait clientWaitClientClosing = new Wait();

				try (ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
					try (Connecter client = ninio.create(proxyClient.factory().header(new Header("_")).with(new Address(Address.LOCALHOST, port)))) {
						client.connect(new Connection() {
							
							@Override
							public void connected(Address address) {
								clientWaitClientConnecting.run();
							}
							
							@Override
							public void received(Address address, ByteBuffer buffer) {
								lock.set(buffer);
							}
							
							@Override
							public void closed() {
								clientWaitClientClosing.run();
							}
							
							@Override
							public void failed(IOException e) {
								lock.fail(e);
							}
						});

						clientWaitClientConnecting.waitFor();
						client.send(null, ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)), new NopConnecterConnectingCallback());
	
						Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("ECHO test");
					}
				}
				clientWaitClientClosing.waitFor();
			}
			serverWaitServerClosing.waitFor();
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
	
}
