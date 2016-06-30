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
	
			Wait wait = new Wait();
			Wait waitForServerClosing = new Wait();
			Wait waitForClientClosing = new Wait();
			final Wait waitServerConnecting = new Wait();
			final Wait waitServerClosing = new Wait();
			final Wait waitClientConnecting = new Wait();
			try (Disconnectable server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port))
				.closing(new WaitClosing(waitForServerClosing)).failing(new LockFailing(lock))
				.connecting(new WaitListenConnecting(wait))
				.listening(new Listening() {
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
				}))) {

				wait.waitFor();

				try (Connector client = ninio.create(TcpSocket.builder().to(new Address(Address.LOCALHOST, port))
					.closing(new WaitClosing(waitForClientClosing)).failing(new LockFailing(lock))
					//.closing(new LockClosing(lock))
					.receiving(new LockReceiver(lock))
					.connecting(new WaitConnecting(waitClientConnecting)))) {

					client.send(null, ByteBufferUtils.toByteBuffer("test"));

					waitClientConnecting.waitFor();
					waitServerConnecting.waitFor();
					Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test");
				}

				waitForClientClosing.waitFor();
				waitServerClosing.waitFor();
			}
			waitForServerClosing.waitFor();
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
	
	@Test
	public void testClose() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
	
			Wait wait = new Wait();
			Wait waitForServerClosing = new Wait();
			Wait waitForClientClosing = new Wait();
			final Wait waitForClientClosingServerSide = new Wait();
			final Wait waitClientConnecting = new Wait();
			try (Disconnectable server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port))
				.closing(new WaitClosing(waitForServerClosing)).failing(new LockFailing(lock))
				.connecting(new WaitListenConnecting(wait))
				.listening(new Listening() {
					@Override
					public Connection connecting(Address from, Connector connector) {
						return new Connection() {
							public Failing failing() {
								return new LockFailing(lock);
							}
							public Connecting connecting() {
								return new Connecting() {
									@Override
									public void connected(Connector conn, Address address) {
										conn.close();
									}
								};
							}
							public Closing closing() {
								return new WaitClosing(waitForClientClosingServerSide);
							}
							public Receiver receiver() {
								return null;
							}
						};
					}
				}))) {

				wait.waitFor();

				try (Connector client = ninio.create(TcpSocket.builder().to(new Address(Address.LOCALHOST, port))
					.closing(new WaitClosing(waitForClientClosing)).failing(new LockFailing(lock))
					//.closing(new LockClosing(lock))
					.connecting(new WaitConnecting(waitClientConnecting)))) {

					waitClientConnecting.waitFor();

					waitForClientClosing.waitFor();
					waitForClientClosingServerSide.waitFor();
				}
			}
			waitForServerClosing.waitFor();
		}
	}
	
}
