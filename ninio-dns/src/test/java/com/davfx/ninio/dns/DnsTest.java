package com.davfx.ninio.dns;

import java.io.IOException;
import java.util.Arrays;

import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;

public class DnsTest {
	
	public static void main(String[] args) throws Exception {
		String host = "dnslookup.fr";

		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			final Lock<byte[], IOException> lock = new Lock<>();
			
			try (DnsConnecter client = DnsTimeout.wrap(1d, ninio.create(DnsClient.builder().with(new SerialExecutor(DnsTest.class))))) {
				client.resolve(host, new DnsReceiver() {
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
