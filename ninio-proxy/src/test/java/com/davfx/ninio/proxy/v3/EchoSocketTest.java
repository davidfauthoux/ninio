package com.davfx.ninio.proxy.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.NinioSocketBuilder;
import com.davfx.ninio.core.Receiver;
import com.davfx.ninio.proxy.ProxyClient;
import com.davfx.ninio.proxy.ProxyConnectorProvider;
import com.davfx.ninio.proxy.ProxyListening;
import com.davfx.ninio.proxy.ProxyServer;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;

public class EchoSocketTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(EchoSocketTest.class);
	
	@Test
	public void testSocket() throws Exception {
		final Lock<String, IOException> lock = new Lock<>();
		
		try (Ninio ninio = Ninio.create()) {
			ExecutorService executor = Executors.newSingleThreadExecutor();
			try {

				final int proxyPort = 8081;

				final Disconnectable proxyServer = ninio.create(ProxyServer.defaultServer(new Address(Address.ANY, proxyPort), new ProxyListening() {
					@Override
					public NinioSocketBuilder<?> create(Address address, String header) {
						if (header.equals("_")) {
							return new EchoNinioSocketBuilder();
						} else {
							return null;
						}
					}
				}));
				try {
					
					final int port = 8080;
			
					final ProxyConnectorProvider proxyClient = ninio.create(ProxyClient.defaultClient(new Address(Address.LOCALHOST, proxyPort)));
					try {
						final Connector client = ninio.create(proxyClient.factory().header("_").with(new Address(Address.LOCALHOST, port))
							.failing(new Failing() {
								@Override
								public void failed(IOException e) {
									LOGGER.warn("Failed <--", e);
									lock.fail(e);
								}
							})
							.closing(new Closing() {
								@Override
								public void closed() {
									LOGGER.debug("Closed <--");
									lock.fail(new IOException("Closed"));
								}
							})
							.receiving(new Receiver() {
								@Override
								public void received(Connector c, Address address, ByteBuffer buffer) {
									String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
									LOGGER.warn("Received {} -->: {}", address, s);
									lock.set(s);
								}
							})
							.connecting(new Connecting() {
								@Override
								public void connected(Connector connector) {
									LOGGER.debug("Client socket connected <--");
								}
							})
						);
						try {
							client.send(null, ByteBuffer.wrap("test".getBytes(Charsets.UTF_8)));
		
							Assertions.assertThat(lock.waitFor()).isEqualTo("ECHO test");
						} finally {
							client.close();
						}
					} finally {
						proxyClient.close();
					}
				} finally {
					proxyServer.close();
				}
			} finally {
				executor.shutdown();
			}
		}
	}
	
	// This test is exactly the same as above, but it is used to check a new SocketReady can be open another time, maybe in the same JVM
	@Test
	public void testSocketSameToCheckClose() throws Exception {
		testSocket();
	}
	
}
