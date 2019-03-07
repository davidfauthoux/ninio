package com.davfx.ninio.ping;

import java.io.IOException;
import java.net.InetAddress;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.util.Lock;

//mvn install dependency:copy-dependencies
//sudo java -cp target/dependency/*:target/test-classes/:target/classes/ com.davfx.ninio.ping.PingTest
public class DoubleClientPingTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(DoubleClientPingTest.class);

	public static void main(String[] args) throws Exception {
		byte[] pingHost0 = InetAddress.getByName("37.187.120.93").getAddress();
		byte[] pingHost1 = InetAddress.getByName("8.8.8.8").getAddress();
		// new byte[] { 8, 8, 8, 8 };
		// ::1

		try (Ninio ninio = Ninio.create()) {
			final List<Lock<Double, IOException>> locks = new LinkedList<Lock<Double, IOException>>();
			for (int i = 0; i < 500; i++) {
				locks.add(new Lock<Double, IOException>());
			}
			
			try (PingConnecter client0 = PingTimeout.wrap(10d, ninio.create(PingClient.builder()))) {
				client0.connect(new PingConnection() {
					@Override
					public void failed(IOException ioe) {
						for (Lock<Double, IOException> lock : locks) {
							lock.fail(ioe);
						}
					}
					@Override
					public void connected(Address address) {
					}
					@Override
					public void closed() {
					}
				});
				try (PingConnecter client1 = PingTimeout.wrap(10d, ninio.create(PingClient.builder()))) {
					client1.connect(new PingConnection() {
						@Override
						public void failed(IOException ioe) {
							for (Lock<Double, IOException> lock : locks) {
								lock.fail(ioe);
							}
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void closed() {
						}
					});
					boolean ping0 = true;
					for (final Lock<Double, IOException> lock : locks) {
						final boolean p = ping0;
						(ping0 ? client0 : client1).ping(ping0 ? pingHost1 : pingHost0, new PingReceiver() {
							@Override
							public void received(double time) {
								LOGGER.debug("From client {}: {}", (p ? "0":"1"), time);
								lock.set(time);
							}
							@Override
							public void failed(IOException ioe) {
								lock.fail(ioe);
							}
						});
						ping0 = !ping0;
						Thread.sleep(10);
					}
					

					int i =0;
					for (Lock<Double, IOException> lock : locks) {
						System.out.println(lock.waitFor());
						System.out.println("---------------------" + i);
						i++;
					}
				}
			}
		}
	}

}
