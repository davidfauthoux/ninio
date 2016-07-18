package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.Limit;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.NopConnecterConnectingCallback;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;

public class HttpGetTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpGetTest.class);

	private static final int LIMIT = 2;
	
	private static Lock<Object, IOException> getRequest(HttpClient client, Timeout timeout, Limit limit, String url, boolean keepAlive) throws IOException {
		final Lock<Object, IOException> lock = new Lock<>();
		HttpRequestBuilder b = HttpTimeout.wrap(timeout, 1d, HttpLimit.wrap(limit, client.request()));
		HttpContentSender s = b.build(HttpRequest.of(url, HttpMethod.GET, keepAlive ? ImmutableMultimap.<String, String>of() : ImmutableMultimap.of(HttpHeaderKey.CONNECTION, HttpHeaderValue.CLOSE)));
		b.receive(new HttpReceiver() {
				@Override
				public void failed(IOException ioe) {
					lock.fail(ioe);
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
			});
		s.finish();
		return lock;
	}
	
	private static void getRequest(HttpClient client, Timeout timeout, Limit limit, String url, boolean keepAlive, String expected) throws IOException {
		Assertions.assertThat(getRequest(client, timeout, limit, url, keepAlive).waitFor()).isEqualTo(expected);
	}
	
	private static Lock<Object, IOException> postRequest(HttpClient client, Timeout timeout, Limit limit, String url, boolean keepAlive, String post) throws IOException {
		final Lock<Object, IOException> lock = new Lock<>();
		HttpRequestBuilder b = HttpTimeout.wrap(timeout, 1d, HttpLimit.wrap(limit, client.request()));
		HttpContentSender s = b.build(HttpRequest.of(url, HttpMethod.POST, keepAlive ? ImmutableMultimap.<String, String>of() : ImmutableMultimap.of(HttpHeaderKey.CONNECTION, HttpHeaderValue.CLOSE)));
		b.receive(new HttpReceiver() {
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
			});
		s.send(ByteBuffer.wrap(post.getBytes(Charsets.UTF_8)), new NopConnecterConnectingCallback());
		s.finish();
		return lock;
	}
	
	private static void postRequest(HttpClient client, Timeout timeout, Limit limit, String url, boolean keepAlive, String post, String expected) throws IOException {
		Assertions.assertThat(postRequest(client, timeout, limit, url, keepAlive, post).waitFor()).isEqualTo(expected);
	}
	
	private static Disconnectable server(Ninio ninio, int port, final String suffix) {
		final Wait waitForClosing = new Wait();
		final Listener tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)));
		tcp.listen(HttpListening.builder().with(new SerialExecutor(HttpGetTest.class)).with(new HttpListeningHandler() {
						
						@Override
						public void connected() {
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
						public HttpContentReceiver handle(final HttpRequest request, final ResponseHandler responseHandler) {
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
									sender.send(ByteBuffer.wrap(b), new NopConnecterConnectingCallback());
									sender.finish();
								}
							};
						}
					})
					
					.build()
				);
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
	
	public static void main(String[] args) throws Exception {
		final double TIMEOUT = 30d;
		final int LIMIT = 10; // Max number of concurrent HTTP requests
		
		try (Ninio ninio = Ninio.create()) { // Should always be created globally
			Executor executor = new SerialExecutor(HttpGetTest.class);
			Limit limit = new Limit(LIMIT);
			try (Timeout timeout = new Timeout()) {
				try (HttpClient client = ninio.create(HttpClient.builder().with(executor))) {
					HttpRequestBuilder b = HttpTimeout.wrap(timeout, TIMEOUT, HttpLimit.wrap(limit, client.request()));
					HttpContentSender s = b.build(HttpRequest.of("http://google.com"));
					b.receive(new HttpReceiver() {
							@Override
							public void failed(IOException e) {
								e.printStackTrace();
							}
							@Override
							public HttpContentReceiver received(HttpResponse response) {
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
						});
					s.finish();
					
					Thread.sleep(2000);
				}
			}
		}
	}
	
	@Test
	public void testSimpleGet() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (Disconnectable tcp = server(ninio, port)) {
				try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
					getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", true, "/test1");
				}
			}
		}
	}

	@Test
	public void testSimplePost() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
					postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", true, "TEST1", "TEST1");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testSimpleGetConnectionClosed() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
					getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", false, "/test1");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testDoubleGet() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
					getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", true, "/test1");
					getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", true, "/test2");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testDoublePost() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
					postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", true, "TEST1", "TEST1");
					postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", true, "TEST2", "TEST2");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testDoubleGetConnectionClosed() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
					getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", false, "/test1");
					getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", false, "/test2");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testGetServerRestarted() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
				Disconnectable tcp = server(ninio, port);
				try {
					getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", true, "/test1");
				} finally {
					tcp.close();
				}
				Thread.sleep(100);
				tcp = server(ninio, port);
				try {
					getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", true, "/test2");
				} finally {
					tcp.close();
				}
			}
		}
	}

	@Test
	public void testGetServerRestartedConnectionClose() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
				Disconnectable tcp = server(ninio, port);
				try {
					getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", false, "/test1");
				} finally {
					tcp.close();
				}
				Thread.sleep(100);
				tcp = server(ninio, port);
				try {
					getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", false, "/test2");
				} finally {
					tcp.close();
				}
			}
		}
	}

	@Test
	public void testPostServerRestarted() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
				Disconnectable tcp = server(ninio, port);
				try {
					postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", true, "TEST1", "TEST1");
				} finally {
					tcp.close();
				}
				Thread.sleep(100);
				tcp = server(ninio, port);
				try {
					postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", true, "TEST2", "TEST2");
				} finally {
					tcp.close();
				}
			}
		}
	}

	@Test
	public void testPostServerRestartedConnectionClose() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
				Disconnectable tcp = server(ninio, port);
				try {
					postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", false, "TEST1", "TEST1");
				} finally {
					tcp.close();
				}
				Thread.sleep(100);
				tcp = server(ninio, port);
				try {
					postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", false, "TEST2", "TEST2");
				} finally {
					tcp.close();
				}
			}
		}
	}

	@Ignore
	@Test
	public void testPostServerRestartedWhilePosting() throws Exception {
		final int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						Disconnectable tcp = server(ninio, port, "_a");
						try {
							try {
								Thread.sleep(280);
							} catch (InterruptedException ie) {
							}
						} finally {
							LOGGER.debug("FIRST CLOSED");
							tcp.close();
						}

						tcp = server(ninio, port, "_b");
						try {
							try {
								Thread.sleep(300);
							} catch (InterruptedException ie) {
							}
						} finally {
							LOGGER.debug("CLOSED");
							tcp.close();
						}

						tcp = server(ninio, port, "_c");
						try {
							try {
								Thread.sleep(400);
							} catch (InterruptedException ie) {
							}
						} finally {
							LOGGER.debug("CLOSED AGAIN");
							tcp.close();
						}
					}
				}).start();
				Thread.sleep(100);
				
				final String url = "http://" + Address.LOCALHOST + ":" + port + "/test0";
				final Lock<Object, IOException> lock = new Lock<>();
				HttpRequestBuilder b = HttpTimeout.wrap(timeout, 1d, HttpLimit.wrap(limit, client.request()));
				HttpContentSender s = b.build(HttpRequest.of(url, HttpMethod.POST, ImmutableMultimap.<String, String>of()));
				b.receive(new HttpReceiver() {
						@Override
						public void failed(IOException e) {
							LOGGER.debug("FAILED", e);
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
					});
				Thread.sleep(100);
				byte[] post = "TEST0".getBytes(Charsets.UTF_8);
				for (int i = 0; i < post.length; i++) {
					s.send(ByteBuffer.wrap(post, i, 1), new NopConnecterConnectingCallback());
					LOGGER.debug("WAITING");
					Thread.sleep(20);
				}
				s.finish();
				//Assertions.assertThat(lock.waitFor()).isEqualTo("TEST0_a");

				postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", true, "TEST1", "TEST1_b");
				Thread.sleep(100);
				postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", true, "TEST2", "TEST2_b");
				postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test3", true, "TEST3", "TEST3_b");
				Thread.sleep(100);
				postRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test4", true, "TEST4", "TEST4_c");
			}
		}
	}

	@Test
	public void testParallelGet() throws Exception {
		int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().with(new SerialExecutor(HttpGetTest.class)))) {
					Lock<Object, IOException> lock1 = getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", true);
					Lock<Object, IOException> lock2 = getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", true);
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
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (HttpClient client = ninio.create(HttpClient.builder().pipelining().with(new SerialExecutor(HttpGetTest.class)))) {
					Lock<Object, IOException> lock1 = getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test1", true);
					Lock<Object, IOException> lock2 = getRequest(client, timeout, limit, "http://" + Address.LOCALHOST + ":" + port + "/test2", true);
					Assertions.assertThat(lock1.waitFor()).isEqualTo("/test1");
					Assertions.assertThat(lock2.waitFor()).isEqualTo("/test2");
				}
			} finally {
				tcp.close();
			}
		}
	}

}
