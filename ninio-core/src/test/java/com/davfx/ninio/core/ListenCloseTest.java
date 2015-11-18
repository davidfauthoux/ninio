package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.Lock;
import com.google.common.base.Charsets;

public class ListenCloseTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ListenCloseTest.class);
	
	@Test
	public void testSocket() throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		final Lock<String, IOException> lockEcho = new Lock<>();
		final Lock<Boolean, IOException> lockClosed = new Lock<>();
		
		try (Queue queue = new Queue()) {
			
			final int port = 8080;
			final SocketListening.Listening[] listening = new SocketListening.Listening[] { null };
	
			{
				Listen listen = new SocketListen(queue.getSelector(), queue.allocator());
		
				new QueueListen(queue, listen).listen(new Address(Address.LOCALHOST, port), new SocketListening() {
					@Override
					public void listening(Listening l) {
						listening[0] = l;
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
						lockClosed.set(true);
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
			
			listening[0].disconnect();
			listening[0].close();
			queue.finish().waitFor();
			LOGGER.debug("Listening closed");
			
			Assertions.assertThat(lockClosed.waitFor()).isEqualTo(true);
			queue.finish().waitFor();
		}
	}

}
