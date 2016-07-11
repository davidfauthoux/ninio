package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class AsyncSocketTest {

	@Test
	public void testSocket() throws Exception {
		final Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
	
			Wait wait = new Wait();
			final Wait waitClosing = new Wait();
			final Wait waitServerConnecting = new Wait();
			final Wait waitServerClosing = new Wait();
			try (Disconnectable server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port))
				.closing(new WaitClosing(waitClosing)).failing(new LockFailing(lock))
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
							@Override
							public Buffering buffering() {
								return null;
							}
						};
					}
				}))) {

				wait.waitFor();

				AsyncTcpSocket client = new AsyncTcpSocket(ninio);
				try {
					client.connect(new Address(Address.LOCALHOST, port)).get();

					client.write(ByteBufferUtils.toByteBuffer("test"));
					
					Future<ByteBuffer> f = client.read();

					waitServerConnecting.waitFor();
					Assertions.assertThat(ByteBufferUtils.toString(f.get())).isEqualTo("test");
				} finally {
					client.close().get();
				}

				waitServerClosing.waitFor();
			}
			
			waitClosing.waitFor();
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
	
}
