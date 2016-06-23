package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;

import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

// Mac OS X:
// sudo chmod go=r /dev/bpf*

@Ignore
public class TcpdumpTest {
	
	static {
		ConfigUtils.override("com.davfx.ninio.core.tcpdump.mode = hex"); // raw not working on Mac OS X
	}
	
	@Test
	public void testDatagram() throws Exception {
		Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
	
			Wait wait = new Wait();
			try (Connector server = ninio.create(TcpdumpSocket.builder().on("lo0").rule("dst port " + port).bind(new Address(Address.ANY, port))
				.failing(new LockFailing(lock))
				.closing(new LockClosing(lock))
				.receiving(new EchoReceiver())
				.connecting(new WaitConnecting(wait)))) {
				
				wait.waitFor();
				
				Thread.sleep(100);
				
				try (Connector client = ninio.create(UdpSocket.builder()
					.failing(new LockFailing(lock))
					.closing(new LockClosing(lock))
					.receiving(new LockReceiver(lock)))) {

					client.send(new Address(Address.LOCALHOST, port), ByteBufferUtils.toByteBuffer("test"));
					Assertions.assertThat(ByteBufferUtils.toString(lock.waitFor())).isEqualTo("test");
				}
			}
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new DatagramReady can be open another time, maybe in the same JVM
	@Test
	public void testDatagramSameToCheckClose() throws Exception {
		testDatagram();
	}

}
