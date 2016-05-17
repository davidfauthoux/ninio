package com.davfx.ninio.ping.v3;

import java.io.IOException;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.v3.Ninio;
import com.davfx.util.Lock;

public class PingTest {
	public static void main(String[] args) throws Exception {
		new PingTest().test();
	}
	@Test
	public void test() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			final Lock<String, IOException> lock = new Lock<>();
			PingClient snmpClient = ninio.create(PingClient.builder().receiving(new PingReceiver() {
				@Override
				public void received(String host, double time) {
					lock.set(host + ":" + time);
				}
			}));
			try {
				snmpClient.ping("8.8.8.8");
				//snmpClient.ping("::1");
				System.out.println(lock.waitFor());
				//Assertions.assertThat(lock.waitFor().toString()).isEqualTo("127.0.0.1");
			} finally {
				snmpClient.close();
			}
		}
	}

}
