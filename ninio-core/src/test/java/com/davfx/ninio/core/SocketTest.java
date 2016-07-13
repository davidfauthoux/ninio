package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class SocketTest {

	@Test
	public void testSocket() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
	
			Wait serverWaitConnecting = new Wait();
			Wait serverWaitClosing = new Wait();
			final Wait serverWaitClientConnecting = new Wait();
			final Wait serverWaitClientClosing = new Wait();
			try (Listener.Listening server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port))).listen(
				new WaitConnectedListenerCallback(serverWaitConnecting,
				new WaitClosedListenerCallback(serverWaitClosing,
				new LockFailedListenerCallback(lock,
				new Listener.Callback() {
					@Override
					public void failed(IOException ioe) {
					}
					@Override
					public void connected() {
					}
					@Override
					public void closed() {
					}
					
					@Override
					public Connecting connecting() {
						return new Connecting() {
							private Connecter.Connecting connecting;
							
							@Override
							public void connecting(Address from, Connecter.Connecting connecting) {
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
				}))))) {

				serverWaitConnecting.waitFor();

				Wait clientWaitConnecting = new Wait();
				Wait clientWaitClosing = new Wait();
				Wait clientWaitSent = new Wait();

				try (Connecter.Connecting client = ninio.create(TcpSocket.builder().to(new Address(Address.LOCALHOST, port))).connect(
						new WaitConnectedConnecterCallback(clientWaitConnecting, 
						new WaitClosedConnecterCallback(clientWaitClosing, 
						new LockFailedConnecterCallback(lock, 
						new LockReceivedConnecterCallback(lock,
						new NopConnecterCallback())))))) {
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
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
}
