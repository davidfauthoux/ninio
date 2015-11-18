package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.Lock;
import com.google.common.base.Charsets;

public class CoreTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(CoreTest.class);
	
	@Test
	public void testDatagram() throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		
		try (Queue queue = new Queue()) {
			
			final int port = 8080;
	
			{
				Ready ready = new DatagramReady(queue.getSelector(), queue.allocator()).bind();
		
				new QueueReady(queue, ready).connect(new Address(null, port), new ReadyConnection() {
					private CloseableByteBufferHandler write;
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Received {} <--: {}", address, s);
						lock.set(s);
						write.close();
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						LOGGER.debug("Connected <--");
						this.write = write;
					}
					
					@Override
					public void close() {
						LOGGER.debug("Closed <--");
						lock.fail(new IOException("Closed"));
					}
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed <--", e);
						lock.fail(e);
					}
				});
			}
			queue.finish().waitFor();
			{
				Ready ready = new DatagramReady(queue.getSelector(), queue.allocator());
		
				new QueueReady(queue, ready).connect(new Address(Address.LOCALHOST, port), new ReadyConnection() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.warn("Received {} -->: {}", address, s);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						LOGGER.debug("Connected -->");
						write.handle(new Address(Address.LOCALHOST, port), ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));
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
			}
			
			Assertions.assertThat(lock.waitFor()).isEqualTo("test");
			queue.finish().waitFor();
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new DatagramReady can be open another time, maybe in the same JVM
	@Test
	public void testDatagramSameToCheckClose() throws Exception {
		testDatagram();
	}
	
	@Test
	public void testSocket() throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		final Lock<String, IOException> lockEcho = new Lock<>();
		
		try (Queue queue = new Queue()) {
			
			final int port = 8080;
	
			{
				Listen listen = new SocketListen(queue.getSelector(), queue.allocator());
		
				new QueueListen(queue, listen).listen(new Address(Address.LOCALHOST, port), new SocketListening() {
					private Listening listening;
					@Override
					public void listening(Listening listening) {
						this.listening = listening;
					}
					
					@Override
					public CloseableByteBufferHandler connected(Address address, final CloseableByteBufferHandler connection) {
						LOGGER.debug("Connected {} <--", address);
						return new CloseableByteBufferHandler() {
							@Override
							public void close() {
								LOGGER.debug("Peer closed <--");
								lock.fail(new IOException("Peer closed"));
								lockEcho.fail(new IOException("Peer closed"));
							}
							
							@Override
							public void handle(Address address, ByteBuffer buffer) {
								String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
								LOGGER.debug("Received {} <--: {}", address, s);
								connection.handle(null, ByteBuffer.wrap("echo".getBytes(Charsets.UTF_8)));
								lock.set(s);
								listening.close();
							}
						};
					}
					
					@Override
					public void close() {
						LOGGER.debug("Closed <--");
						lock.fail(new IOException("Closed"));
						lockEcho.fail(new IOException("Closed"));
					}
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed <--", e);
						lock.fail(e);
						lockEcho.fail(e);
					}
				});
			}
			queue.finish().waitFor();
			{
				Ready ready = new SocketReady(queue.getSelector(), queue.allocator());
		
				new QueueReady(queue, ready).connect(new Address(Address.LOCALHOST, port), new ReadyConnection() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Received {} -->: {}", address, s);
						lockEcho.set(s);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						LOGGER.debug("Connected -->");
						write.handle(new Address(Address.LOCALHOST, port), ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));
					}
					
					@Override
					public void close() {
						LOGGER.debug("Closed -->");
						lock.fail(new IOException("Closed"));
						lockEcho.fail(new IOException("Closed"));
					}
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed -->", e);
						lock.fail(e);
						lockEcho.fail(e);
					}
				});
			}
			
			Assertions.assertThat(lock.waitFor()).isEqualTo("test");
			Assertions.assertThat(lockEcho.waitFor()).isEqualTo("echo");
			queue.finish().waitFor();
		}
	}

	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}

	@Test
	public void testSSlSocket() throws Exception {
		// System.setProperty("javax.net.debug", "ssl:handshake");
		
		final Lock<String, IOException> lock = new Lock<>();
		final Lock<String, IOException> lockEcho = new Lock<>();
		
		// Trust trust = new Trust(new File("src/test/resources/keystore.jks"), "test-password", new File("src/test/resources/keystore.jks"), "test-password");
		Trust trust = new Trust("/keystore.jks", "test-password", "/keystore.jks", "test-password");
		
		try (Queue queue = new Queue()) {
			
			final int port = 8080;
	
			{
				Listen listen = new SocketListen(queue.getSelector(), queue.allocator());
		
				new QueueListen(queue, listen).listen(new Address(Address.LOCALHOST, port), new SslSocketListening(trust, queue.allocator(), new SocketListening() {
					private Listening listening;
					@Override
					public void listening(Listening listening) {
						this.listening = listening;
					}
					
					@Override
					public CloseableByteBufferHandler connected(Address address, final CloseableByteBufferHandler connection) {
						LOGGER.debug("Connected {} <--", address);
						return new CloseableByteBufferHandler() {
							@Override
							public void close() {
								LOGGER.debug("Peer closed <--");
								lock.fail(new IOException("Peer closed"));
								lockEcho.fail(new IOException("Peer closed"));
							}
							
							@Override
							public void handle(Address address, ByteBuffer buffer) {
								String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
								LOGGER.debug("Received {} <--: {}", address, s);
								connection.handle(null, ByteBuffer.wrap("echo".getBytes(Charsets.UTF_8)));
								lock.set(s);
								listening.close();
							}
						};
					}
					
					@Override
					public void close() {
						LOGGER.debug("Closed <--");
						lock.fail(new IOException("Closed"));
						lockEcho.fail(new IOException("Closed"));
					}
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed <--", e);
						lock.fail(e);
						lockEcho.fail(e);
					}
				}));
			}
			queue.finish().waitFor();
			{
				Ready ready = new SslReady(trust, queue.allocator(), new SocketReady(queue.getSelector(), queue.allocator()));
		
				new QueueReady(queue, ready).connect(new Address(Address.LOCALHOST, port), new ReadyConnection() {
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Received {} -->: {}", address, s);
						lockEcho.set(s);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						LOGGER.debug("Connected -->");
						write.handle(new Address(Address.LOCALHOST, port), ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));
					}
					
					@Override
					public void close() {
						LOGGER.debug("Closed -->");
						lock.fail(new IOException("Closed"));
						lockEcho.fail(new IOException("Closed"));
					}
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed -->", e);
						lock.fail(e);
						lockEcho.fail(e);
					}
				});
			}
			
			Assertions.assertThat(lock.waitFor()).isEqualTo("test");
			Assertions.assertThat(lockEcho.waitFor()).isEqualTo("echo");
			queue.finish().waitFor();
		}
	}
}
