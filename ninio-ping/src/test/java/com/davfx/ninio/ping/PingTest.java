package com.davfx.ninio.ping;

import java.io.IOException;

import com.davfx.ninio.core.LockFailing;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.RawSocket;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.ping.PingClient;
import com.davfx.ninio.ping.PingReceiver;
import com.davfx.ninio.ping.PingTimeout;
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
			
			try (PingClient client = ninio.create(PingClient.builder().with(RawSocket.builder().failing(new LockFailing(lock))).with(new SerialExecutor(PingTest.class)))) {
				PingTimeout.wrap(timeout, 1d, client.request(), new LockFailing(lock)).receiving(new PingReceiver() {
					@Override
					public void received(double time) {
						lock.set(time);
					}
				}).ping(pingHost);
				
				System.out.println(lock.waitFor());
			}
		}
	}

}
