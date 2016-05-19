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
		
		try (Ninio ninio = Ninio.create()) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			try {
				final int port = 8080;
		
				final Wait wait = new Wait();
				final Disconnectable server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port))
					.failing(new Failing() {
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Failed <--", e);
							lock.fail(e);
						}
					})
					.connecting(new ListenConnecting() {
						@Override
						public void connected(Disconnectable connector) {
							LOGGER.debug("Server connected <--");
							wait.run();
						}
					})
					.listening(new Listening() {
						@Override
						public void connecting(Address from, Connector connector, SocketBuilder<?> builder) {
							builder.failing(new Failing() {
								@Override
								public void failed(IOException e) {
									LOGGER.warn("Socket failed <--", e);
									lock.fail(e);
								}
							});
							builder.connecting(new Connecting() {
								@Override
								public void connected(Connector connector) {
									LOGGER.debug("Socket connected <--");
									wait.run();
								}
							});
							builder.closing(new Closing() {
								@Override
								public void closed() {
									LOGGER.debug("Socket closed <--");
									lock.fail(new IOException("Closed"));
								}
							});
							builder.receiving(new Receiver() {
								@Override
								public void received(Connector connector, Address address, ByteBuffer buffer) {
									String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
									LOGGER.debug("Received {} <--: {}", address, s);
									connector.send(null, ByteBuffer.wrap("response".getBytes(Charsets.UTF_8)));
								}
							});
						}
					}));
				try {
					wait.waitFor();
	
					final Connector client = ninio.create(TcpSocket.builder().to(new Address(Address.LOCALHOST, port))
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
							public void received(Connector c, Address address, ByteBuffer buffer) {
								String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
								LOGGER.warn("Received {} -->: {}", address, s);
								lock.set(s);
							}
						})
						.connecting(new Connecting() {
							@Override
							public void connected(Connector connector) {
								LOGGER.debug("Client socket connected <--");
							}
						}));
					try {
						client.send(null, ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));
	
						Assertions.assertThat(lock.waitFor()).isEqualTo("response");
					} finally {
						client.close();
					}
				} finally {
					server.close();
				}
			} finally {
				executor.shutdown();
			}
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
	
}
