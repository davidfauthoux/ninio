package com.davfx.ninio.proxy;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.ping.PingClient;
import com.davfx.ninio.ping.PingReceiver;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.proxy.ProxyConnectorProvider;
import com.davfx.ninio.proxy.ProxyServer;
import com.davfx.ninio.util.Lock;

public class PingTest {

	public static void main(String[] args) throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int proxyPort = 8081;

			try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), null))) {
				try (ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
					final String pingHost = "8.8.8.8"; // "::1"
					try (PingClient pingClient = ninio.create(PingClient.builder().with(proxyClient.raw()))) {
						pingClient.request().receiving(new PingReceiver() {
							@Override
							public void received(double time) {
								lock.set(pingHost + ":" + time);
							}
						}).ping(pingHost);

						System.out.println(lock.waitFor());
					}
				}
			}
		}
	}
	
}
