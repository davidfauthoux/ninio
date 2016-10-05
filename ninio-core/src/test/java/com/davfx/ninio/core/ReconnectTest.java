package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;

public class ReconnectTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ReconnectTest.class);
	
	@Test
	public void test() throws Exception {
		final Lock<Boolean, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			int port = 8080;
	
			Wait serverWaitConnecting = new Wait();
			Wait serverWaitClosing = new Wait();
			final Wait serverWaitClientConnecting = new Wait();
			final Wait serverWaitClientClosing = new Wait();
			try (Listener server = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
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
						public Connection connecting(Connected connecting) {
							return new Connection() {
								@Override
								public void received(Address address, ByteBuffer buffer) {
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

				try (Connecter client = ninio.create(ReconnectConnecter.builder().with(TcpSocket.builder().to(new Address(Address.LOCALHOST, port))))) {
					client.connect(new Connection() {
						boolean connected = false;
						@Override
						public void received(Address address, ByteBuffer buffer) {
						}
						
						@Override
						public void connected(Address address) {
							if (connected) {
								lock.set(true);
							} else {
								connected = true;
							}
						}
						
						@Override
						public void closed() {
							LOGGER.debug("---------> Closed");
						}
						
						@Override
						public void failed(IOException e) {
							LOGGER.debug("---------> Failed", e);
						}
					});
					
					serverWaitClientConnecting.waitFor();
					
					server.close();
					serverWaitClosing.waitFor();

					LOGGER.debug("Creating second server");
					Wait server2WaitConnecting = new Wait();
					Wait server2WaitClosing = new Wait();
					final Wait server2WaitClientConnecting = new Wait();
					final Wait server2WaitClientClosing = new Wait();
					try (Listener server2 = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)))) {
						server2.listen(
								new WaitConnectedListening(server2WaitConnecting,
								new WaitClosedListening(server2WaitClosing,
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
									public Connection connecting(Connected connecting) {
										return new Connection() {
											@Override
											public void received(Address address, ByteBuffer buffer) {
											}
											
											@Override
											public void failed(IOException ioe) {
												lock.fail(ioe);
											}
											@Override
											public void connected(Address address) {
												server2WaitClientConnecting.run();
											}
											@Override
											public void closed() {
												server2WaitClientClosing.run();
											}
										};
									}
								}))));

						server2WaitConnecting.waitFor();
						server2WaitClientConnecting.waitFor();

						Assertions.assertThat(lock.waitFor()).isTrue();
					}
					server2WaitClosing.waitFor();
				}

				serverWaitClientClosing.waitFor();
			}
		}
	}
}
