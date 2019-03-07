package com.davfx.ninio.ping;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Ninio;

//mvn install dependency:copy-dependencies
//sudo java -cp target/dependency/*:target/test-classes/:target/classes/ com.davfx.ninio.ping.PingTest
public class PingTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(PingTest.class);

	public static void main(String[] args) throws Exception {
		byte[] pingHost = new byte[] { 8, 8, 8, 8 };
		// ::1

		try (Ninio ninio = Ninio.create()) {
			try (PingConnecter client = PingTimeout.wrap(1d, ninio.create(PingClient.builder()))) {
				client.connect(new PingConnection() {
					@Override
					public void failed(IOException ioe) {
						LOGGER.error("Failed", ioe);
					}
					@Override
					public void connected(Address address) {
					}
					@Override
					public void closed() {
					}
				});
				while (true) {
					client.ping(pingHost, new PingReceiver() {
						@Override
						public void received(double time) {
							LOGGER.info("---> {}", time);
						}
						@Override
						public void failed(IOException ioe) {
							LOGGER.error("Failed", ioe);
						}
					});
					Thread.sleep(100L);
				}
			}
		}
	}

}
