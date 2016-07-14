package com.davfx.ninio.ping;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;

// mvn install dependency:copy-dependencies
// sudo java -cp target/dependency/*:target/test-classes/:target/classes/ com.davfx.ninio.ping.PingTest
public class PingTest {
	
	public static void main(String[] args) throws Exception {
		String pingHost = "8.8.8.8";
		// ::1

		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			final Lock<Double, IOException> lock = new Lock<>();
			
			try (PingConnecter.Connecting client = PingTimeout.wrap(1d, ninio.create(PingClient.builder().with(new SerialExecutor(PingTest.class))).connect(new PingConnecter.Callback() {
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
			}))) {
				client.ping(pingHost, new PingConnecter.Connecting.Callback() {
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
