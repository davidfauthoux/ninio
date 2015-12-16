package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.http.websocket.WebsocketHttpServerHandler;
import com.davfx.ninio.http.websocket.WebsocketReady;
import com.davfx.ninio.util.QueueScheduled;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;

public class WebsocketTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(WebsocketTest.class);

	@Test
	public void test() throws Exception {
		int port = 8080;
		try (Queue queue = new Queue()) {
			try (HttpServer server = new HttpServer(queue, null, new Address(Address.ANY, port), new HttpServerHandlerFactory() {
				@Override
				public void failed(IOException e) {
					LOGGER.error("Failed", e);
				}
				@Override
				public void closed() {
				}
				
				@Override
				public HttpServerHandler create() {
					return new WebsocketHttpServerHandler(false, new ReadyConnection() {
						private CloseableByteBufferHandler write;
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
							LOGGER.debug("Received {} <--: {}", address, s);
							write.handle(null, ByteBuffer.wrap(("echo " + s).getBytes(Charsets.UTF_8)));
						}
						
						@Override
						public void connected(FailableCloseableByteBufferHandler write) {
							LOGGER.debug("Connected <--");
							this.write = write;
						}
						
						@Override
						public void close() {
							LOGGER.debug("Closed <--");
						}
						
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Failed <--", e);
						}
					});
				}
				
			})) {
				
				queue.finish().waitFor();
				
				final Lock<String, IOException> lock = new Lock<>();
				try (Http http = new Http()) {
					new WebsocketReady(http.client()).connect(new Address(Address.LOCALHOST, port), new ReadyConnection() {
						private FailableCloseableByteBufferHandler write;
						
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
							if (s.equals("echo test")) {
								LOGGER.debug("Received {} -->: {}", address, s);
								write.handle(null, ByteBuffer.wrap("test2".getBytes(Charsets.UTF_8)));
							} else if (s.equals("echo test2")) {
								LOGGER.debug("Received {} -->: {}", address, s);
								write.handle(null, ByteBuffer.wrap("test3".getBytes(Charsets.UTF_8)));
							} else {
								LOGGER.debug("Received? {} -->: {}", address, s);
								lock.set(s);
							}
						}
						
						@Override
						public void connected(final FailableCloseableByteBufferHandler write) {
							this.write = write;
							LOGGER.debug("Connected -->");
							write.handle(null, ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));
						}
						
						@Override
						public void close() {
							LOGGER.debug("Closed -->");
							lock.fail(new IOException("Closed"));
						}
						
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Failed -->", e);
							lock.fail(e);
						}
					});
					
					Assertions.assertThat(lock.waitFor()).isEqualTo("echo test3");
				}
			}
			queue.finish().waitFor();
		}
	}
	
	@Test
	public void testWithInitialDelay() throws Exception {
		int port = 8080;
		try (Queue queue = new Queue()) {
			try (HttpServer server = new HttpServer(queue, null, new Address(Address.ANY, port), new HttpServerHandlerFactory() {
				@Override
				public void failed(IOException e) {
					LOGGER.error("Failed", e);
				}
				@Override
				public void closed() {
					LOGGER.debug("Server closed");
				}
				
				@Override
				public HttpServerHandler create() {
					return new WebsocketHttpServerHandler(false, new ReadyConnection() {
						private CloseableByteBufferHandler write;
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
							LOGGER.debug("Server received {} <--: {}", address, s);
							write.handle(null, ByteBuffer.wrap(("echo " + s).getBytes(Charsets.UTF_8)));
						}
						
						@Override
						public void connected(FailableCloseableByteBufferHandler write) {
							LOGGER.debug("Connected <--");
							this.write = write;
						}
						
						@Override
						public void close() {
							LOGGER.debug("Closed <--");
						}
						
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Failed <--", e);
						}
					});
				}
				
			})) {
				
				queue.finish().waitFor();
				LOGGER.debug("Server ready");

				final Lock<String, IOException> lock = new Lock<>();
				try (Http http = new Http()) {
					new WebsocketReady(http.client()).connect(new Address(Address.LOCALHOST, port), new ReadyConnection() {
						private FailableCloseableByteBufferHandler write;
						
						@Override
						public void handle(Address address, ByteBuffer buffer) {
							String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
							if (s.equals("echo test")) {
								LOGGER.debug("Received {} -->: {}", address, s);
								write.handle(null, ByteBuffer.wrap("test2".getBytes(Charsets.UTF_8)));
							} else if (s.equals("echo test2")) {
								LOGGER.debug("Received {} -->: {}", address, s);
								write.handle(null, ByteBuffer.wrap("test3".getBytes(Charsets.UTF_8)));
							} else {
								LOGGER.debug("Received? {} -->: {}", address, s);
								lock.set(s);
							}
						}
						
						@Override
						public void connected(final FailableCloseableByteBufferHandler write) {
							this.write = write;
							LOGGER.debug("Connected -->");
							QueueScheduled.run(queue, 0.1d, new Runnable() {
								@Override
								public void run() {
									write.handle(null, ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));
								}
							});
						}
						
						@Override
						public void close() {
							LOGGER.debug("Closed -->");
							lock.fail(new IOException("Closed"));
						}
						
						@Override
						public void failed(IOException e) {
							LOGGER.warn("Failed -->", e);
							lock.fail(e);
						}
					});
					
					Assertions.assertThat(lock.waitFor()).isEqualTo("echo test3");
				}
			}
			queue.finish().waitFor();
		}
	}
}
