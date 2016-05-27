package com.davfx.ninio.proxy.v3;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.Ninio;
import com.davfx.ninio.ping.v3.PingClient;
import com.davfx.ninio.ping.v3.PingReceiver;
import com.davfx.util.Lock;

public class PingTest {

	public static void main(String[] args) throws Exception {
		new PingTest().testSocket();
	}
	
	@Test
	public void testSocket() throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			try {

				final int proxyPort = 8081;

				final Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), null));
				try {
					
					final ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)));
					try {
						final String pingHost = "8.8.8.8";
						try (PingClient pingClient = ninio.create(PingClient.builder().with(proxyClient.raw()))) {
							pingClient.request().receiving(new PingReceiver() {
								@Override
								public void received(double time) {
									lock.set(pingHost + ":" + time);
								}
							}).ping(pingHost);
							//.ping("::1");
							System.out.println(lock.waitFor());
							//Assertions.assertThat(lock.waitFor().toString()).isEqualTo("127.0.0.1");
						}
					} finally {
						proxyClient.close();
					}
				} finally {
					proxyServer.close();
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
