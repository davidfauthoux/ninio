package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class SecureSocketTest {

	@Test
	public void testSocket() throws Exception {
		final Trust trust = new Trust("/keystore.jks", "test-password", "/keystore.jks", "test-password");

		Executor executor = new ThreadingSerialExecutor(SecureSocketTest.class);

		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			final int port = 8080;
	
			Wait wait = new Wait();
			final Wait waitServerConnecting = new Wait();
			final Wait waitServerClosing = new Wait();
			final Wait waitClientConnecting = new Wait();
			try (Disconnectable server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port))
				.failing(new LockFailing(lock))
				.connecting(new WaitListenConnecting(wait))
				.listening(new SecureSocketServerBuilder().with(executor).trust(trust).listening(new Listening() {
					@Override
					public Connection connecting(Address from, Connector connector) {
						return new Connection() {
							public Failing failing() {
								return new LockFailing(lock);
							}
							public Connecting connecting() {
								return new WaitConnecting(waitServerConnecting);
							}
							public Closing closing() {
								return new WaitClosing(waitServerClosing);
							}
							public Receiver receiver() {
								return new EchoReceiver();
							}
						};
					}
				}).build()))) {

				wait.waitFor();

				try (Connector client = ninio.create(new SecureSocketBuilder(TcpSocket.builder()).trust(trust).with(executor).to(new Address(Address.LOCALHOST, port))
					.failing(new LockFailing(lock))
					.closing(new LockClosing(lock))
					.receiving(new LockReceiver(lock))
					.connecting(new WaitConnecting(waitClientConnecting)))) {

					client.send(null, ByteBufferUtils.toByteBuffer("test"));

					waitClientConnecting.waitFor();
					waitServerConnecting.waitFor();
					Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test");
				}

				waitServerClosing.waitFor();
			}
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
	
}