package com.davfx.ninio.proxy;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.ConfigurableNinioBuilder;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.LockFailing;
import com.davfx.ninio.core.LockReceiver;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.WaitClosing;
import com.davfx.ninio.core.WaitConnecting;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Charsets;

public class EchoTest {
	
	@Test
	public void testSocket() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int proxyPort = 8081;

			final Wait waitForServerClosing = new Wait();
			try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new ProxyListening() {
				@Override
				public void closed() {
					waitForServerClosing.run();
				}
				@Override
				public ConfigurableNinioBuilder<Connector, ?> create(Address address, String header) {
					if (header.equals("_")) {
						return new EchoNinioSocketBuilder();
					} else {
						return null;
					}
				}
			}))) {
				int port = 8080;
				
				Wait clientWait = new Wait();
				final Wait waitForClientClosing = new Wait();

				try (ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
					try (Connector client = ninio.create(proxyClient.factory().header(new Header("_")).with(new Address(Address.LOCALHOST, port))
						.failing(new LockFailing(lock))
						.closing(new WaitClosing(waitForClientClosing))
						//.closing(new LockClosing(lock))
						.receiving(new LockReceiver(lock))
						.connecting(new WaitConnecting(clientWait))
					)) {
						client.send(null, ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));
	
						Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("ECHO test");
					}
				}
				waitForClientClosing.waitFor();
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
