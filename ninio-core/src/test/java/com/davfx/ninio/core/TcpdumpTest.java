package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Mutable;
import com.davfx.ninio.util.Wait;

// Mac OS X:
// sudo chmod go=r /dev/bpf*

public class TcpdumpTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(TcpdumpTest.class);
	
	static {
		ConfigUtils.override("com.davfx.ninio.core.tcpdump.mode = hex"); // raw not working on Mac OS X
	}
	
	@Test
	public void test() throws Exception {
		Lock<ByteBuffer, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
			Wait serverWaitConnecting = new Wait();
			Wait serverWaitClosing = new Wait();
			final Mutable<Connecter.Connecting> serverConnecting = new Mutable<>(null);
			try (Connecter.Connecting server = ninio.create(TcpdumpSocket.builder().on("lo0").rule("dst port " + port).bind(new Address(Address.ANY, port))).connect(
				new WaitConnectedConnecterCallback(serverWaitConnecting,
				new WaitClosedConnecterCallback(serverWaitClosing,
				new LockFailedConnecterCallback(lock,
				new Connecter.Callback() {
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
						serverConnecting.value.send(address, buffer, new NopConnecterConnectingCallback());
					}
				}))))) {

				serverConnecting.value = server;
				serverWaitConnecting.waitFor();
				Thread.sleep(100); // Using tcpdump needs to wait a little bit for the process to start 

				Wait clientWaitConnecting = new Wait();
				Wait clientWaitClosing = new Wait();
				Wait clientWaitSent = new Wait();

				try (Connecter.Connecting client = ninio.create(UdpSocket.builder()).connect(
						new WaitConnectedConnecterCallback(clientWaitConnecting, 
						new WaitClosedConnecterCallback(clientWaitClosing, 
						new LockFailedConnecterCallback(lock, 
						new LockReceivedConnecterCallback(lock,
						new NopConnecterCallback())))))) {
					client.send(new Address(Address.LOCALHOST, port), ByteBufferUtils.toByteBuffer("test"),
						new WaitSentConnecterConnectingCallback(clientWaitSent,
						new LockFailedConnecterConnectingCallback(lock,
						new NopConnecterConnectingCallback())));
					
					clientWaitConnecting.waitFor();
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
