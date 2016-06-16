package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Charsets;

public class DatagramTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(DatagramTest.class);
	
	@Test
	public void testDatagram() throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			final int port = 8080;
	
			final Wait wait = new Wait();
			final Connector server = ninio.create(UdpSocket.builder().bind(new Address(Address.ANY, port))
				.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed <--", e);
						lock.fail(e);
					}
				})
				.closing(new Closing() {
					@Override
					public void closed() {
						LOGGER.debug("Closed <--");
						lock.fail(new IOException("Closed"));
					}
				})
				.receiving(new Receiver() {
					@Override
					public void received(Connector conn, Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Received {} <--: {}", address, s);
						conn.send(address, ByteBuffer.wrap("response".getBytes(Charsets.UTF_8)));
					}
				})
				.connecting(new Connecting() {
					@Override
					public void connected(Connector conn, Address address) {
						LOGGER.debug("Connected <--");
						wait.run();
					}
				}));
			try {
				wait.waitFor();

				final Connector client = ninio.create(UdpSocket.builder()
					.failing(new Failing() {
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Failed <--", e);
							lock.fail(e);
						}
					})
					.closing(new Closing() {
						@Override
						public void closed() {
							LOGGER.debug("Closed <--");
							lock.fail(new IOException("Closed"));
						}
					})
					.receiving(new Receiver() {
						@Override
						public void received(Connector conn, Address address, ByteBuffer buffer) {
							String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
							LOGGER.warn("Received {} -->: {}", address, s);
							lock.set(s);
						}
					}));
				try {
					client.send(new Address(Address.LOCALHOST, port), ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));

					Assertions.assertThat(lock.waitFor()).isEqualTo("response");
				} finally {
					client.close();
				}
			} finally {
				server.close();
			}
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new DatagramReady can be open another time, maybe in the same JVM
	@Test
	public void testDatagramSameToCheckClose() throws Exception {
		testDatagram();
	}
	
}
