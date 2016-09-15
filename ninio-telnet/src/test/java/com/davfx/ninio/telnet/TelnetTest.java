package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Connected;
import com.davfx.ninio.core.ConnectingClosingFailing;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.SendCallback;
import com.davfx.ninio.core.TcpSocket;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Function;

public class TelnetTest {
	private static final Logger LOGGER = LoggerFactory.getLogger(TelnetTest.class);

	@Test
	public void test() throws Exception {
		final Function<String, String> f = new Function<String, String>() {
			@Override
			public String apply(String input) {
				input = input.trim();
				LOGGER.debug("--> {}", input);
				String result;
				if (input.equals("Hello")) {
					result = "World!";
				} else if (input.equals("Bye")) {
					result = null;
				} else {
					result = "Did you say " + input + "?";
				}
				LOGGER.debug("<-- {}", result);
				return result;
			}
		};
		
		int port = 8080;
		
		final Lock<Void, IOException> lock = new Lock<>();
		try (Ninio ninio = Ninio.create()) {
			final Wait serverWaitServerListening = new Wait();
			final Wait serverWaitServerClosing = new Wait();
			final Wait serverWaitClientConnecting = new Wait();
			final Wait serverWaitClientClosing = new Wait();
			try (Listener ss = ninio.create(TelnetServer.builder().with(TcpSocketServer.builder().bind(new Address(Address.ANY, port))))) {
				ss.listen(new Listening() {
					@Override
					public Connection connecting(final Connected connecting) {
						return new CuttingReceiver(0, ByteBufferUtils.toByteBuffer(TelnetSpecification.EOL), new Connection() {
							private InMemoryBuffers buffers = new InMemoryBuffers();
							@Override
							public void received(Address address, ByteBuffer buffer) {
								if (buffer == null) {
									String r = f.apply(buffers.toString());
									buffers = new InMemoryBuffers();
									if (r == null) {
										connecting.close();
									} else {
										connecting.send(null, ByteBufferUtils.toByteBuffer(r + TelnetSpecification.EOL), new SendCallback() {
											@Override
											public void failed(IOException e) {
												lock.fail(e);
											}
											@Override
											public void sent() {
											}
										});
									}
								} else {
									buffers.add(buffer);
								}
							}
							
							@Override
							public void closed() {
								serverWaitClientClosing.run();
							}
							@Override
							public void connected(Address address) {
								serverWaitClientConnecting.run();
							}
							@Override
							public void failed(IOException e) {
								lock.fail(e);
							}
						});
					}
					
					@Override
					public void closed() {
						serverWaitServerClosing.run();
					}
					@Override
					public void connected(Address address) {
						serverWaitServerListening.run();
					}
					@Override
					public void failed(IOException e) {
						lock.fail(e);
					}
				});
			
				serverWaitServerListening.waitFor();

				final Lock<String, IOException> lock0 = new Lock<>();
				final Lock<String, IOException> lock1 = new Lock<>();
				
				final Wait clientWaitClientConnecting = new Wait();
				final Wait clientWaitClientClosing = new Wait();
				try (final CutOnPromptClient c = ninio.create(CutOnPromptClient.builder().with(new SerialExecutor(CutOnPromptClient.class))
					.with(TelnetClient.builder().with(TcpSocket.builder().to(new Address(Address.LOCALHOST, port)))))) {
					c.connect(new ConnectingClosingFailing() {
						@Override
						public void connected(Address address) {
							clientWaitClientConnecting.run();
						}
						@Override
						public void closed() {
							clientWaitClientClosing.run();
						}
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
					});

					c.write("Hello", TelnetSpecification.EOL, new CutOnPromptClientReceiver() {
						@Override
						public void received(String result) {
							lock0.set(result);
							LOGGER.debug("---------------> ***{}***", result);
							c.write("Hello again", TelnetSpecification.EOL, new CutOnPromptClientReceiver() {
								@Override
								public void received(String result) {
									LOGGER.debug("---------------> ***{}***", result);
									lock1.set(result);
									c.write("Bye", TelnetSpecification.EOL, new CutOnPromptClientReceiver() {
										@Override
										public void received(String result) {
											LOGGER.debug("---------------> ***{}***", result);
										}
									});
								}
							});
						}
					});

					clientWaitClientConnecting.waitFor();
					serverWaitClientConnecting.waitFor();
					
					Assertions.assertThat(lock0.waitFor()).isEqualTo("World!" + TelnetSpecification.EOL);
					Assertions.assertThat(lock1.waitFor()).isEqualTo("Did you say Hello again?" + TelnetSpecification.EOL);
					serverWaitClientClosing.waitFor(); // Closed with "Bye"
				}
				
				clientWaitClientClosing.waitFor();
			}
			serverWaitServerClosing.waitFor();
			
			lock.set(null);
			lock.waitFor();
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
}
