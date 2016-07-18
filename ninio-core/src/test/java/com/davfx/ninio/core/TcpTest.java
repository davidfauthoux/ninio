package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class TcpTest {

	@Test
	public void test() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
	
			Wait serverWaitConnecting = new Wait();
			Wait serverWaitClosing = new Wait();
			final Wait serverWaitClientConnecting = new Wait();
			final Wait serverWaitClientClosing = new Wait();
			try (Listener server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
				server.listen(
					new WaitConnectedListenerCallback(serverWaitConnecting,
					new WaitClosedListenerCallback(serverWaitClosing,
					new LockFailedListenerCallback(lock,
					new Listener.Callback() {
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
						public Listener.ListenerConnecting connecting() {
							return new Listener.ListenerConnecting() {
								private Connected connecting;
								
								@Override
								public void connecting(Connected connecting) {
									this.connecting = connecting;
								}
								@Override
								public void received(Address address, ByteBuffer buffer) {
									connecting.send(null, buffer, new NopConnecterConnectingCallback());
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

				try (Connecter client = ninio.create(TcpSocket.builder().to(new Address(Address.LOCALHOST, port)))) {
					client.connect(
						new WaitConnectedConnecterCallback(clientWaitConnecting, 
						new WaitClosedConnecterCallback(clientWaitClosing, 
						new LockFailedConnecterCallback(lock, 
						new LockReceivedConnecterCallback(lock,
						new NopConnecterCallback())))));
					client.send(null, ByteBufferUtils.toByteBuffer("test"),
						new WaitSentConnecterConnectingCallback(clientWaitSent,
						new LockFailedConnecterConnectingCallback(lock,
						new NopConnecterConnectingCallback())));
					
					clientWaitConnecting.waitFor();
					serverWaitClientConnecting.waitFor();
					Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test");
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
