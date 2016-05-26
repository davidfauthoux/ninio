package com.davfx.ninio.ping.v3;

import java.io.IOException;
import java.util.concurrent.Executors;

import org.junit.Test;

import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Ninio;
import com.davfx.ninio.core.v3.util.Timeout;
import com.davfx.util.Lock;

public class PingTest {
	
	public static void main(String[] args) throws Exception {
		new PingTest().test();
	}
	
	@Test
	public void test() throws Exception {
		String pingHost = "8.8.8.99";
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			final Lock<Double, IOException> lock = new Lock<>();
			
			try (PingClient client = ninio.create(PingClient.builder().with(Executors.newSingleThreadExecutor()))) {
				PingTimeout.wrap(timeout, 1d, client.request(), new Failing() {
					@Override
					public void failed(IOException e) {
						lock.fail(e);
					}
				}).receiving(new PingReceiver() {
					@Override
					public void received(double time) {
						lock.set(time);
					}
				}).ping(pingHost);
				// ::1
				System.out.println(lock.waitFor());
				//Assertions.assertThat(lock.waitFor())
			}
		}
	}

}
