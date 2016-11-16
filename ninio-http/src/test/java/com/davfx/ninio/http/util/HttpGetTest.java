package com.davfx.ninio.http.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ByteBufferUtils;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpContentSender;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.HttpListeningHandler;
import com.davfx.ninio.http.HttpMessage;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.HttpReceiver;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpStatus;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Charsets;

public class HttpGetTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpGetTest.class);
	
	private static final String LOCALHOST = "localhost";//127.0.0.1";

	private static Disconnectable server(Ninio ninio, int port, final String suffix) {
		final Wait waitForClosing = new Wait();
		final Listener tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)));
		tcp.listen(ninio.create(HttpListening.builder().with(new HttpListeningHandler() {
						
						@Override
						public void connected(Address address) {
						}

						@Override
						public void closed() {
							LOGGER.info("Closed");
							waitForClosing.run();
						}
						
						@Override
						public void failed(IOException ioe) {
							LOGGER.error("Failed", ioe);
						}
						
						@Override
						public HttpContentReceiver handle(final HttpRequest request, final HttpResponseSender responseHandler) {
							LOGGER.debug("----> {}", request);
							return new HttpContentReceiver() {
								private final InMemoryBuffers post = new InMemoryBuffers();
								@Override
								public void received(ByteBuffer buffer) {
									post.add(buffer);
								}
								@Override
								public void ended() {
									byte[] b;
									if (request.method == HttpMethod.GET) {
										b = (request.path + suffix).getBytes(Charsets.UTF_8);
									} else {
										b = (post.toString(Charsets.UTF_8) + suffix).getBytes(Charsets.UTF_8);
									}
									HttpContentSender sender = responseHandler.send(new HttpResponse(HttpStatus.OK, HttpMessage.OK));//, ImmutableMultimap.of(HttpHeaderKey.CONTENT_LENGTH, String.valueOf(b.length))));
									sender.send(ByteBuffer.wrap(b), new Nop());
									sender.finish();
								}
							};
						}
					})
					
				));
		return new Disconnectable() {
			@Override
			public void close() {
				tcp.close();
				waitForClosing.waitFor();
			}
		};
	}
	private static Disconnectable server(Ninio ninio, int port) {
		return server(ninio, port, "");
	}
	
	@Test
	public void testSimpleGet() throws Exception {
		int port = 8080;
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable tcp = server(ninio, port)) {
				try (HttpClient client = new HttpClient()) {
					final Lock<Object, IOException> lock = new Lock<>();
					client.request().url("http://" + LOCALHOST + ":" + port + "/test1").receive(new HttpReceiver() {
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
						@Override
						public HttpContentReceiver received(HttpResponse response) {
							return new HttpContentReceiver() {
								private final InMemoryBuffers b = new InMemoryBuffers();
								@Override
								public void received(ByteBuffer buffer) {
									LOGGER.debug("-----------------> {}", new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
									b.add(buffer);
								}
								@Override
								public void ended() {
									LOGGER.debug("-----------------> END {}", b.toString());
									lock.set(b.toString());
								}
							};
						}
					}).get();
					Assertions.assertThat(lock.waitFor()).isEqualTo("/test1");
				}
			}
		}
	}

	@Test
	public void testSimplePost() throws Exception {
		int port = 8080;
		try (Ninio ninio = Ninio.create()) {
			try (Disconnectable tcp = server(ninio, port)) {
				try (HttpClient client = new HttpClient()) {
					final Lock<Object, IOException> lock = new Lock<>();
					client.request().url("http://" + LOCALHOST + ":" + port + "/test1").receive(new HttpReceiver() {
						@Override
						public void failed(IOException e) {
							lock.fail(e);
						}
						@Override
						public HttpContentReceiver received(HttpResponse response) {
							return new HttpContentReceiver() {
								private final InMemoryBuffers b = new InMemoryBuffers();
								@Override
								public void received(ByteBuffer buffer) {
									LOGGER.debug("-----------------> {}", new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
									b.add(buffer);
								}
								@Override
								public void ended() {
									LOGGER.debug("-----------------> END {}", b.toString());
									lock.set(b.toString());
								}
							};
						}
					}).post().send(ByteBufferUtils.toByteBuffer("TEST1"), new Nop()).finish();;
					Assertions.assertThat(lock.waitFor()).isEqualTo("TEST1");
				}
			}
		}
	}

}
