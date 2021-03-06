package com.davfx.ninio.ping;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.util.Lock;

//mvn install dependency:copy-dependencies
//sudo java -cp target/dependency/*:target/test-classes/:target/classes/ com.davfx.ninio.ping.PingTest
public class PingTest {
	public static void main(String[] args) throws Exception {
		byte[] pingHost = new byte[] { 8, 8, 8, 8 };
		// ::1

		try (Ninio ninio = Ninio.create()) {
			final Lock<Double, IOException> lock = new Lock<>();
			
			try (PingConnecter client = PingTimeout.wrap(1d, ninio.create(PingClient.builder()))) {
				client.connect(new PingConnection() {
					@Override
					public void failed(IOException ioe) {
						lock.fail(ioe);
					}
					@Override
					public void connected(Address address) {
					}
					@Override
					public void closed() {
					}
				});
				client.ping(pingHost, new PingReceiver() {
					@Override
					public void received(double time) {
						lock.set(time);
					}
					@Override
					public void failed(IOException ioe) {
						lock.fail(ioe);
					}
				});
				
				System.out.println(lock.waitFor());
			}
		}
	}

}
