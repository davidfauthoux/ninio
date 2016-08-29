package com.davfx.ninio.dns;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.util.Arrays;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.dns.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;

public class DnsTest {
	
	static {
		ConfigUtils.load(new Dependencies(), DnsTest.class.getPackage().getName());
	}
	
	public static void main(String[] args) throws Exception {
		String host = "google.com";

		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			final Lock<byte[], IOException> lock = new Lock<>();
			
			try (DnsConnecter client = DnsTimeout.wrap(1d, ninio.create(DnsClient.builder().with(new SerialExecutor(DnsTest.class))))) {
				client.connect(new DnsConnection() {
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
				});
				client.request().resolve(host, StandardProtocolFamily.INET6).receive(new DnsReceiver() {
					@Override
					public void received(byte[] ip) {
						lock.set(ip);
					}
					@Override
					public void failed(IOException ioe) {
						lock.fail(ioe);
					}
				});
				
				System.out.println(Arrays.toString(lock.waitFor()));
			}
		}
	}

}
