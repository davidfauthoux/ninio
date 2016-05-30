package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.ExecutorUtils;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.Ninio;
import com.davfx.ninio.core.v3.TcpSocketServer;
import com.davfx.ninio.core.v3.util.Limit;
import com.davfx.ninio.core.v3.util.Timeout;
import com.davfx.ninio.http.InMemoryBuffers;
import com.davfx.util.ClassThreadFactory;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;

public class HttpGetTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpGetTest.class);

	private static final int LIMIT = 2;
	
	private static Lock<Object, IOException> request(HttpClient client, Timeout timeout, Limit limit, String url) throws IOException {
		final Lock<Object, IOException> lock = new Lock<>();
		HttpTimeout.wrap(timeout, 1d, HttpLimit.wrap(limit, client.request()))
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
			})
			.build(HttpRequest.of(url)).finish();
		return lock;
	}
	
	private static void request(HttpClient client, Timeout timeout, Limit limit, String url, String expected) throws IOException {
		Assertions.assertThat(request(client, timeout, limit, url).waitFor()).isEqualTo(expected);
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
							@Override
							public void received(ByteBuffer buffer) {
							}
							@Override
							public void ended() {
								HttpContentSender sender = responseHandler.send(HttpResponse.ok());
								sender.send(ByteBuffer.wrap(request.path.getBytes(Charsets.UTF_8))).finish();
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
	
	public static void main(String[] args) throws Exception {
		final double TIMEOUT = 30d;
		final int LIMIT = 10; // Max number of concurrent HTTP requests
		
		try (Ninio ninio = Ninio.create()) { // Should always be created globally
			ExecutorService executor = Executors.newSingleThreadExecutor(new ClassThreadFactory(HttpGetTest.class));
			try {
				try (Timeout timeout = new Timeout(); Limit limit = new Limit(LIMIT)) {
					try (HttpClient client = ninio.create(HttpClient.builder().with(executor))) {
						HttpTimeout.wrap(timeout, TIMEOUT, HttpLimit.wrap(limit, client.request()))
						
							.failing(new Failing() {
								@Override
								public void failed(IOException e) {
									e.printStackTrace();
								}
							})
						
							.receiving(new HttpReceiver() {
								@Override
								public HttpContentReceiver received(Disconnectable disconnectable, HttpResponse response) {
									System.out.println("Response = " + response);
									return new HttpContentReceiver() {
										private final InMemoryBuffers b = new InMemoryBuffers();
										@Override
										public void received(ByteBuffer buffer) {
											b.add(buffer);
										}
										@Override
										public void ended() {
											System.out.println("Content = " + b.toString());
										}
									};
								}
							})
							
							.build(HttpRequest.of("http://google.com")).finish();
						
						Thread.sleep(2000);
					}
				}
			} finally {
				ExecutorUtils.shutdown(executor);
			}
		}
	}
	
	@Test
	public void testGet() throws Exception {
		int port = 8080;
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout(); Limit limit = new Limit(LIMIT)) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().with(Executors.newSingleThreadExecutor()))) {
					request(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", "/test1");
					request(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", "/test2");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testParallelGet() throws Exception {
		int port = 8080;
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout(); Limit limit = new Limit(LIMIT)) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().with(Executors.newSingleThreadExecutor()))) {
					Lock<Object, IOException> lock1 = request(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1");
					Lock<Object, IOException> lock2 = request(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2");
					Assertions.assertThat(lock1.waitFor()).isEqualTo("/test1");
					Assertions.assertThat(lock2.waitFor()).isEqualTo("/test2");
				}
			} finally {
				tcp.close();
			}
		}
	}

	// Check in the log that only one socket is created
	@Test
	public void testPipeliningGet() throws Exception {
		int port = 8080;
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout(); Limit limit = new Limit(LIMIT)) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().pipelining().with(Executors.newSingleThreadExecutor()))) {
					Lock<Object, IOException> lock1 = request(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1");
					Lock<Object, IOException> lock2 = request(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2");
					Assertions.assertThat(lock1.waitFor()).isEqualTo("/test1");
					Assertions.assertThat(lock2.waitFor()).isEqualTo("/test2");
				}
			} finally {
				tcp.close();
			}
		}
	}

}
