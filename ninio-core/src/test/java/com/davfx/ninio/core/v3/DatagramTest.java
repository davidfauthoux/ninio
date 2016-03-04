package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.util.Lock;
import com.davfx.util.Wait;
import com.google.common.base.Charsets;

public class DatagramTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(DatagramTest.class);
	
	@Test
	public void testDatagram() throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			final int port = 8080;
	
			final Wait wait = new Wait();
			final Connectable server = new DatagramReady().with(executor).bind(new Address(null, port)).create();
			try {
				server.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed <--", e);
						lock.fail(e);
					}
				});
				server.closing(new Closing() {
					@Override
					public void closed() {
						LOGGER.debug("Closed <--");
						lock.fail(new IOException("Closed"));
					}
				});
				server.receiving(new Receiver() {
					@Override
					public void received(Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Received {} <--: {}", address, s);
						server.send(address, ByteBuffer.wrap("response".getBytes(Charsets.UTF_8)));
					}
				});
				server.connecting(new Connecting() {
					@Override
					public void connected() {
						LOGGER.debug("Connected <--");
						wait.run();
					}
				});
				
				server.connect();
				wait.waitFor();

				final Connectable client = new DatagramReady().with(executor).create();
				try {
					client.failing(new Failing() {
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Failed <--", e);
							lock.fail(e);
						}
					});
					client.closing(new Closing() {
						@Override
						public void closed() {
							LOGGER.debug("Closed <--");
							lock.fail(new IOException("Closed"));
						}
					});
					client.receiving(new Receiver() {
						@Override
						public void received(Address address, ByteBuffer buffer) {
							String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
							LOGGER.warn("Received {} -->: {}", address, s);
							lock.set(s);
						}
					});
	
					client.connect();
					client.send(new Address(Address.LOCALHOST, port), ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));

					Assertions.assertThat(lock.waitFor()).isEqualTo("response");
				} finally {
					client.disconnect();
				}
			} finally {
				server.disconnect();
			}
		} finally {
			executor.shutdown();
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new DatagramReady can be open another time, maybe in the same JVM
	@Test
	public void testDatagramSameToCheckClose() throws Exception {
		testDatagram();
	}
	
}
