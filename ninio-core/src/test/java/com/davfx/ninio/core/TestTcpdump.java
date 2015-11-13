package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.Lock;
import com.google.common.base.Charsets;

// Mac OS X:
// sudo chmod go=r /dev/bpf*

public class TestTcpdump {

	private static final Logger LOGGER = LoggerFactory.getLogger(TestTcpdump.class);
	
	@Test
	public void test() throws Exception {
		System.setProperty("ninio.tcpdump.mode", "hex"); // raw not working on Mac OS X
		final Lock<String, IOException> lock = new Lock<>();
		
		Queue queue = new Queue();
		
		final int port = 8080;

		TcpdumpSyncDatagramReady.Receiver receiver = new TcpdumpSyncDatagramReady.Receiver(new TcpdumpSyncDatagramReady.DestinationPortRule(port), "lo0");
		{
			Ready ready = new TcpdumpSyncDatagramReady(receiver).bind();
	
			new QueueReady(queue, ready).connect(new Address(Address.LOCALHOST, port), new ReadyConnection() { // Address MUST be specified for the packet to be mapped
				@Override
				public void handle(Address address, ByteBuffer buffer) {
					String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
					LOGGER.debug("Received {} <--: {}", address, s);
					lock.set(s);
				}
				
				@Override
				public void connected(FailableCloseableByteBufferHandler write) {
					LOGGER.debug("Connected <--");
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
		
		Thread.sleep(1000);
		
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
					write.handle(new Address(Address.LOCALHOST, port), ByteBuffer.wrap(("test").getBytes(Charsets.UTF_8)));
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
	}
	
}
