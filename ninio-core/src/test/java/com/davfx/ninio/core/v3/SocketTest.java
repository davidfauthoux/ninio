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

public class SocketTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(SocketTest.class);
	
	@Test
	public void testSocket() throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		
		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			final int port = 8080;
	
			final Wait wait = new Wait();
			final Acceptable server = new SocketListen().with(executor).bind(new Address(null, port)).create();
			try {
				server.failing(new Failing() {
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed <--", e);
						lock.fail(e);
					}
				});
				
				server.accepting(new Accepting() {
					@Override
					public void connected() {
						LOGGER.debug("Accepting connected <--");
						wait.run();
					}
				});
				
				server.accept(new Acceptable.Listening() {
					@Override
					public void connecting(final Connectable connectable) {
						connectable.failing(new Failing() {
							@Override
							public void failed(IOException e) {
								LOGGER.warn("Socket failed <--", e);
								lock.fail(e);
							}
						});
						connectable.connecting(new Connecting() {
							@Override
							public void connected() {
								LOGGER.debug("Socket connected <--");
								wait.run();
							}
						});
						connectable.closing(new Closing() {
							@Override
							public void closed() {
								LOGGER.debug("Socket closed <--");
								lock.fail(new IOException("Closed"));
							}
						});
						connectable.receiving(new Receiver() {
							@Override
							public void received(Address address, ByteBuffer buffer) {
								String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
								LOGGER.debug("Received {} <--: {}", address, s);
								connectable.send(null, ByteBuffer.wrap("response".getBytes(Charsets.UTF_8)));
							}
						});
						
						connectable.connect();
					}
				});

				wait.waitFor();

				final Connectable client = new SocketReady().with(executor).connect(new Address(Address.LOCALHOST, port)).create();
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
					client.connecting(new Connecting() {
						@Override
						public void connected() {
							LOGGER.debug("Client socket connected <--");
						}
					});

					client.connect();
					client.send(null, ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));

					Assertions.assertThat(lock.waitFor()).isEqualTo("response");
				} finally {
					client.disconnect();
				}
			} finally {
				server.close();
			}
		} finally {
			executor.shutdown();
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
	
}
