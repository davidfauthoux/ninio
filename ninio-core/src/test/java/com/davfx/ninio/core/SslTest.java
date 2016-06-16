package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Charsets;

public class SslTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(SslTest.class);
	
	@Test
	public void testSocket() throws Exception {
		final Trust trust = new Trust("/keystore.jks", "test-password", "/keystore.jks", "test-password");

		Executor executor = new ThreadingSerialExecutor(SslTest.class);

		final Lock<String, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
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
				.listening(new SecureSocketServerBuilder().with(executor).trust(trust).listening(new Listening() {
					@Override
					public Connection connecting(Address from, Connector connector) {
						return new Connection() {
							@Override
							public Failing failing() {
								return new Failing() {
									@Override
									public void failed(IOException e) {
										LOGGER.warn("Socket failed <--", e);
										lock.fail(e);
									}
								};
							}
							@Override
							public Connecting connecting() {
								return new Connecting() {
									@Override
									public void connected(Connector conn, Address address) {
										LOGGER.debug("Socket connected <--");
										wait.run();
									}
								};
							}
							@Override
							public Closing closing() {
								return new Closing() {
									@Override
									public void closed() {
										LOGGER.debug("Socket closed <--");
										lock.fail(new IOException("Closed"));
									}
								};
							}
							@Override
							public Receiver receiver() {
								return new Receiver() {
									@Override
									public void received(Connector conn, Address address, ByteBuffer buffer) {
										String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
										LOGGER.debug("Received {} <--: {}", address, s);
										conn.send(null, ByteBuffer.wrap("response".getBytes(Charsets.UTF_8)));
									}
								};
							}
						};
					}
				}).build()));
			try {
				wait.waitFor();

				final Connector client = ninio.create(new SecureSocketBuilder(TcpSocket.builder()).trust(trust).with(executor).to(new Address(Address.LOCALHOST, port))
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
							LOGGER.debug("Received {} -->: {}", address, s);
							lock.set(s);
						}
					})
					.connecting(new Connecting() {
						@Override
						public void connected(Connector conn, Address address) {
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
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
	
}
