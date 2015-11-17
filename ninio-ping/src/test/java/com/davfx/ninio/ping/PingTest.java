package com.davfx.ninio.ping;

import java.io.IOException;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.ping.PingClientHandler.Callback.PingCallback;
import com.davfx.util.Lock;

public class PingTest {
	@Test
	public void test() throws Exception {
		final Lock<Double, IOException> lock = new Lock<>();
		new Ping().ping(Address.LOCALHOST, new PingCallback() {
			@Override
			public void failed(IOException e) {
				lock.fail(e);
			}
			@Override
			public void pong(double time) {
				lock.set(time);
			}
		});
		Assertions.assertThat(lock.waitFor()).isGreaterThan(0d);
	}
}
