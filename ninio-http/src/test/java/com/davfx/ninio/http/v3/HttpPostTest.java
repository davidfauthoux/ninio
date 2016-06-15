package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpContentReceiver;
import com.davfx.ninio.http.HttpContentSender;
import com.davfx.ninio.http.HttpListening;
import com.davfx.ninio.http.HttpListeningHandler;
import com.davfx.ninio.http.HttpReceiver;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.InMemoryBuffers;
import com.davfx.ninio.http.HttpListeningHandler.ConnectionHandler;
import com.davfx.ninio.http.HttpListeningHandler.ConnectionHandler.ResponseHandler;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;

public class HttpPostTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpPostTest.class);
	
	private static Lock<Object, IOException> request(HttpClient client, String url, String post) throws IOException {
		final Lock<Object, IOException> lock = new Lock<>();
		client.request()
			.failing(new Failing() {
				@Override
				public void failed(IOException e) {
					lock.fail(e);
				}
			})
			.receiving(new HttpReceiver() {
				@Override
				public HttpContentReceiver received(Disconnectable disconnectable, HttpResponse response) {
					return new HttpContentReceiver() {
						private final InMemoryBuffers b = new InMemoryBuffers();
						@Override
						public void received(ByteBuffer buffer) {
							b.add(buffer);
						}
						@Override
						public void ended() {
							lock.set(b.toString());
						}
					};
				}
			})
			.build()
			.create(HttpRequest.of(url)).send(ByteBuffer.wrap(post.getBytes(Charsets.UTF_8))).finish();
		return lock;
	}
	
	private static void request(HttpClient client, String url, String post, String expected) throws IOException {
		Assertions.assertThat(request(client, url, post).waitFor()).isEqualTo(expected);
	}
	
	private static Disconnectable server(Ninio ninio, int port) {
		Disconnectable tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)).listening(HttpListening.builder().with(Executors.newSingleThreadExecutor()).with(new HttpListeningHandler() {
			@Override
			public ConnectionHandler create() {
				return new ConnectionHandler() {
					@Override
					public HttpContentReceiver handle(final HttpRequest request, final ResponseHandler responseHandler) {
						LOGGER.debug("----> {}", request);
						return new HttpContentReceiver() {
							private final InMemoryBuffers b = new InMemoryBuffers();
							@Override
							public void received(ByteBuffer buffer) {
								b.add(buffer);
							}
							@Override
							public void ended() {
								HttpContentSender sender = responseHandler.send(HttpResponse.ok());
								sender.send(ByteBuffer.wrap(b.toString().getBytes(Charsets.UTF_8))).finish();
							}
						};
					}
					@Override
					public void closed() {
					}
				};
			}
		}).build()));
		return tcp;
	}
	
	@Test
	public void testPost() throws Exception {
		int port = 8080;
		try (Ninio ninio = Ninio.create()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().with(Executors.newSingleThreadExecutor()))) {
					request(client, "http://" + Address.LOCALHOST + ":" + port + "/test1", "TEST1", "TEST1");
					request(client, "http://" + Address.LOCALHOST + ":" + port + "/test2", "TEST2", "TEST2");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testParallelPost() throws Exception {
		int port = 8080;
		try (Ninio ninio = Ninio.create()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().with(Executors.newSingleThreadExecutor()))) {
					Lock<Object, IOException> lock1 = request(client, "http://" + Address.LOCALHOST + ":" + port + "/test1", "TEST1");
					Lock<Object, IOException> lock2 = request(client, "http://" + Address.LOCALHOST + ":" + port + "/test2", "TEST2");
					Assertions.assertThat(lock1.waitFor()).isEqualTo("TEST1");
					Assertions.assertThat(lock2.waitFor()).isEqualTo("TEST2");
				}
			} finally {
				tcp.close();
			}
		}
	}

	// Check in the log that only one socket is created
	@Test
	public void testPipeliningPost() throws Exception {
		int port = 8080;
		try (Ninio ninio = Ninio.create()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().pipelining().with(Executors.newSingleThreadExecutor()))) {
					Lock<Object, IOException> lock1 = request(client, "http://" + Address.LOCALHOST + ":" + port + "/test1", "TEST1");
					Lock<Object, IOException> lock2 = request(client, "http://" + Address.LOCALHOST + ":" + port + "/test2", "TEST2");
					Assertions.assertThat(lock1.waitFor()).isEqualTo("TEST1");
					Assertions.assertThat(lock2.waitFor()).isEqualTo("TEST2");
				}
			} finally {
				tcp.close();
			}
		}
	}

}
