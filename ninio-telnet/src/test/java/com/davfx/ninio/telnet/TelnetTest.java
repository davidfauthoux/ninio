package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Buffering;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.Listening;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Receiver;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.WaitClosing;
import com.davfx.ninio.core.WaitConnecting;
import com.davfx.ninio.telnet.CutOnPromptClient.Handler;
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
		
		try (Ninio ninio = Ninio.create()) {
			final Wait waitForServerListening = new Wait();
			Wait waitForServerClosing = new Wait();
			final Wait waitForClientConnectingServerSide = new Wait();
			final Wait waitForClientClosingServerSide = new Wait();
			try (Disconnectable ss = ninio.create(TelnetServer.builder().listening(new Listening() {
				@Override
				public Connection connecting(Address from, final Connector connector) {
					waitForServerListening.run();
					return new Listening.Connection() {
						@Override
						public Receiver receiver() {
							return new CuttingReceiver(0, ByteBufferUtils.toByteBuffer(TelnetSpecification.EOL), new Receiver() {
								private InMemoryBuffers buffers = new InMemoryBuffers();
								@Override
								public void received(Connector conn, Address address, ByteBuffer buffer) {
									if (buffer == null) {
										String r = f.apply(buffers.toString());
										buffers = new InMemoryBuffers();
										if (r == null) {
											conn.close();
										} else {
											conn.send(null, ByteBufferUtils.toByteBuffer(r + TelnetSpecification.EOL));
										}
									} else {
										buffers.add(buffer);
									}
								}
							});
						}
						@Override
						public Failing failing() {
							return null;
						}
						@Override
						public Connecting connecting() {
							return new WaitConnecting(waitForClientConnectingServerSide);
						}
						@Override
						public Closing closing() {
							return new WaitClosing(waitForClientClosingServerSide);
						}
						@Override
						public Buffering buffering() {
							return null;
						}
					};
				}
			}).with(TcpSocketServer.builder().closing(new WaitClosing(waitForServerClosing)).bind(new Address(Address.ANY, port))))) {
				final Lock<String, IOException> lock0 = new Lock<>();
				final Lock<String, IOException> lock1 = new Lock<>();
				
				Wait waitForClientClosing = new Wait();
				try (Disconnectable c = ninio.create(CutOnPromptClient.builder().with(new SerialExecutor(CutOnPromptClient.class)).with(new Handler() {
					@Override
					public void connected(Write write) {
						write.write("Hello", TelnetSpecification.EOL, new Handler.Receive() {
							@Override
							public void received(Write write, String result) {
								lock0.set(result);
								LOGGER.debug("---------------> ***{}***", result);
								write.write("Hello again", TelnetSpecification.EOL, new Handler.Receive() {
									@Override
									public void received(Write write, String result) {
										LOGGER.debug("---------------> ***{}***", result);
										lock1.set(result);
										write.write("Bye", TelnetSpecification.EOL, new Handler.Receive() {
											@Override
											public void received(Write write, String result) {
												LOGGER.debug("---------------> ***{}***", result);
											}
										});
									}
								});
							}
						});
					}
				}).with(TelnetClient.builder().closing(new WaitClosing(waitForClientClosing)).to(new Address(Address.LOCALHOST, port))))) {
					waitForServerListening.waitFor();
					waitForClientConnectingServerSide.waitFor();
					Assertions.assertThat(lock0.waitFor()).isEqualTo("World!" + TelnetSpecification.EOL);
					Assertions.assertThat(lock1.waitFor()).isEqualTo("Did you say Hello again?" + TelnetSpecification.EOL);
					waitForClientClosing.waitFor();
				}
				
				waitForClientClosingServerSide.waitFor();
			}
			waitForServerClosing.waitFor();
		}
	}
	
	@Test
	public void testSameToCheckClose() throws Exception {
		test();
	}
}
