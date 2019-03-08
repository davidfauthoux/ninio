package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class UdpTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(UdpTest.class);
	
	@Test
	public void test() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
	
			Wait serverWaitConnecting = new Wait();
			Wait serverWaitClosing = new Wait();
			try (Connecter server = ninio.create(UdpSocket.builder().bind(new Address(Address.ANY, port)))) {
				server.connect(
					new WaitConnectedConnection(serverWaitConnecting,
					new WaitClosedConnection(serverWaitClosing,
					new LockFailedConnection(lock,
					new Connection() {
						@Override
						public void failed(IOException ioe) {
						}
						@Override
						public void connected(Address address) {
						}
						@Override
						public void closed() {
						}
						
						@Override
						public void received(Address address, ByteBuffer buffer) {
							LOGGER.debug("Received: {}", ByteBufferUtils.toString(buffer));
							server.send(address, buffer, new Nop());
						}
					}))));

				serverWaitConnecting.waitFor();

				Wait clientWaitConnecting = new Wait();
				Wait clientWaitClosing = new Wait();
				Wait clientWaitSent = new Wait();

				try (Connecter client = ninio.create(UdpSocket.builder())) {
					client.connect(
						new WaitConnectedConnection(clientWaitConnecting, 
						new WaitClosedConnection(clientWaitClosing, 
						new LockFailedConnection(lock, 
						new LockReceivedConnection(lock,
						new Nop())))));
					clientWaitConnecting.waitFor();
					client.send(new Address(Address.LOCALHOST, port), ByteBufferUtils.toByteBuffer("test"),
						new WaitSentSendCallback(clientWaitSent,
						new LockSendCallback(lock,
						new Nop())));
					
					Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test");
				}

				clientWaitClosing.waitFor();
			}
			serverWaitClosing.waitFor();
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
	
}
