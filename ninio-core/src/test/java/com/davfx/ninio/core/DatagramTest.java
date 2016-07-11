package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class DatagramTest {

	@Test
	public void testDatagram() throws Exception {
		Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
	
			Wait wait = new Wait();
			Wait waitForServerClosing = new Wait();
			Wait waitForClientClosing = new Wait();
			try (Connector server = ninio.create(UdpSocket.builder().bind(new Address(Address.ANY, port))
				.closing(new WaitClosing(waitForServerClosing)).failing(new LockFailing(lock))
				//.closing(new LockClosing(lock))
				.receiving(new EchoReceiver())
				.connecting(new WaitConnecting(wait)))) {
				
				wait.waitFor();

				try (Connector client = ninio.create(UdpSocket.builder()
					.closing(new WaitClosing(waitForClientClosing)).failing(new LockFailing(lock))
					//.closing(new LockClosing(lock))
					.receiving(new LockReceiver(lock)))) {

					client.send(new Address(Address.LOCALHOST, port), ByteBufferUtils.toByteBuffer("test"));
					Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test");
				}
				
				waitForClientClosing.waitFor();
			}
			waitForServerClosing.waitFor();
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new DatagramReady can be open another time, maybe in the same JVM
	@Test
	public void testDatagramSameToCheckClose() throws Exception {
		testDatagram();
	}
	
}
