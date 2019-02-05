package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class SecureTest {
	public void test() throws Exception {
		final Trust trust = new Trust("/keystore.jks", "test-password", "/keystore.jks", "test-password");

		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			final int port = 8080;
	
			Wait serverWaitConnecting = new Wait();
			Wait serverWaitClosing = new Wait();
			final Wait serverWaitClientConnecting = new Wait();
			final Wait serverWaitClientClosing = new Wait();
			try (Listener server = ninio.create(new SecureSocketServerBuilder(TcpSocketServer.builder()).trust(trust).bind(new Address(Address.ANY, port)))) {
				server.listen(
					new WaitConnectedListening(serverWaitConnecting,
					new WaitClosedListening(serverWaitClosing,
					new LockListening(lock,
					new Listening() {
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
						public Connection connecting(final Connected connecting) {
							return new Connection() {
								@Override
								public void received(Address address, ByteBuffer buffer) {
									connecting.send(null, ByteBufferUtils.toByteBuffer("ECHO:" + ByteBufferUtils.toString(buffer)), new Nop());
								}
								
								@Override
								public void failed(IOException ioe) {
									lock.fail(ioe);
								}
								@Override
								public void connected(Address address) {
									serverWaitClientConnecting.run();
								}
								@Override
								public void closed() {
									serverWaitClientClosing.run();
								}
							};
						}
					}))));

				serverWaitConnecting.waitFor();

				Wait clientWaitConnecting = new Wait();
				Wait clientWaitClosing = new Wait();
				Wait clientWaitSent = new Wait();

				try (Connecter client = ninio.create(new SecureSocketBuilder(TcpSocket.builder()).trust(trust).to(new Address(Address.LOCALHOST, port)))) {
					client.connect(
						new WaitConnectedConnection(clientWaitConnecting, 
						new WaitClosedConnection(clientWaitClosing, 
						new LockFailedConnection(lock, 
						new LockReceivedConnection(lock,
						new Nop())))));
					client.send(null, ByteBufferUtils.toByteBuffer("loooooooooooongtest"),
						new WaitSentSendCallback(clientWaitSent,
						new LockSendCallback(lock,
						new Nop())));
					
					clientWaitConnecting.waitFor();
					serverWaitClientConnecting.waitFor();
					Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("ECHO:loooooooooooongtest");
				}

				clientWaitClosing.waitFor();
				serverWaitClientClosing.waitFor();
			}
			serverWaitClosing.waitFor();
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
	
}
