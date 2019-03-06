package com.davfx.ninio.proxy;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.ping.PingClient;
import com.davfx.ninio.ping.PingConnecter;
import com.davfx.ninio.ping.PingConnection;
import com.davfx.ninio.ping.PingReceiver;
import com.davfx.ninio.ping.PingTimeout;
import com.davfx.ninio.util.Lock;

//mvn install dependency:copy-dependencies
//sudo java -cp target/dependency/*:target/test-classes/:target/classes/ com.davfx.ninio.proxy.PingTest
public class ProxyOpenLeaveOpenPingTest {

	public static void main(String[] args) throws Exception {
		byte[] pingHost = new byte[] { 8, 8, 8, 8 };
		// ::1

		int proxyPort = 8081;
		
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), null))) {
				try (ProxyProvider proxyClient0 = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
					final Lock<Double, IOException> lock0 = new Lock<>();
					try (PingConnecter client0 = PingTimeout.wrap(1d, ninio.create(PingClient.builder().with(proxyClient0.raw())))) {
						client0.connect(new PingConnection() {
							@Override
							public void failed(IOException ioe) {
								lock0.fail(ioe);
							}
							@Override
							public void connected(Address address) {
							}
							@Override
							public void closed() {
							}
						});
						client0.ping(pingHost, new PingReceiver() {
							@Override
							public void received(double time) {
								lock0.set(time);
							}
							@Override
							public void failed(IOException ioe) {
								lock0.fail(ioe);
							}
						});
						
						System.out.println(lock0.waitFor());
						try (ProxyProvider proxyClient1 = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)))) {
							final Lock<Double, IOException> lock1 = new Lock<>();
							try (PingConnecter client1 = PingTimeout.wrap(1d, ninio.create(PingClient.builder().with(proxyClient1.raw())))) {
								client1.connect(new PingConnection() {
									@Override
									public void failed(IOException ioe) {
										lock1.fail(ioe);
									}
									@Override
									public void connected(Address address) {
									}
									@Override
									public void closed() {
									}
								});
								client1.ping(pingHost, new PingReceiver() {
									@Override
									public void received(double time) {
										lock1.set(time);
									}
									@Override
									public void failed(IOException ioe) {
										lock1.fail(ioe);
									}
								});
								
								System.out.println(lock1.waitFor());
							}
						}
					}
				}
			}
		}
	}
	
}
