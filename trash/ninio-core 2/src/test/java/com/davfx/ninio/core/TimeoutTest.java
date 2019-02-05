package com.davfx.ninio.core;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TimeoutTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(TimeoutTest.class);
	
	@Test
	public void test() throws Exception {
		try (Timeout timeout = new Timeout()) {

			final boolean[] failed = new boolean[] { false };
			
			Runnable r = new Runnable() {
				@Override
				public void run() {
					LOGGER.debug("----");
				}
			};
			
			Timeout.Manager m = timeout.set(1d);
			m.run(new Runnable() {
				@Override
				public void run() {
					synchronized (failed) {
						failed[0] = true;
					}
				}
			});
	
			for (int i = 0; i < 5; i++) {
				Thread.sleep(500);
				m.reset();
				r.run();
			}

			synchronized (failed) {
				Assertions.assertThat(failed[0]).isFalse();
			}

			Thread.sleep(2000);

			synchronized (failed) {
				Assertions.assertThat(failed[0]).isTrue();
			}

		}
	}
}
