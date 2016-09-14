package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Executor;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;
import com.davfx.ninio.core.InMemoryBuffers;
import com.davfx.ninio.core.Limit;
import com.davfx.ninio.core.Listener;
import com.davfx.ninio.core.Ninio;
import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.TcpSocketServer;
import com.davfx.ninio.core.Timeout;
import com.davfx.ninio.dns.DnsClient;
import com.davfx.ninio.dns.DnsConnecter;
import com.davfx.ninio.dns.DnsConnection;
import com.davfx.ninio.util.Lock;
import com.davfx.ninio.util.SerialExecutor;
import com.davfx.ninio.util.Wait;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;

public class HttpGetTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpGetTest.class);
	
	private static final String LOCALHOST = "localhost";//127.0.0.1";

	private static final int LIMIT = 2;
	
	@SuppressWarnings("unused")
	private static void example() {
		final Limit limit = new Limit();
		final Timeout timeout = new Timeout();
		Ninio ninio = Ninio.create();
		Executor executor = new SerialExecutor(HttpGetTest.class);
		
		String url = "http://...";
		DnsConnecter dns = ninio.create(DnsClient.builder().with(executor));
		dns.connect(new DnsConnection() {
			@Override
			public void closed() {
			}
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void connected(Address address) {
			}
		});

		final HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(executor));

		HttpRequest.resolve(dns, url, new HttpRequest.ResolveCallback() {
			@Override
			public void failed(IOException e) {
			}
			
			@Override
			public void resolved(HttpRequest request) {
				{
					HttpTimeout.wrap(timeout, 1d, HttpLimit.wrap(limit, LIMIT, client.request()))
						.build(request)
						.receive(new HttpReceiver() {
							@Override
							public void failed(IOException ioe) {
							}
							@Override
							public HttpContentReceiver received(HttpResponse response) {
								return new HttpContentReceiver() {
									private final InMemoryBuffers b = new InMemoryBuffers();
									@Override
									public void received(ByteBuffer buffer) {
										b.add(buffer);
									}
									@Override
									public void ended() {
										LOGGER.debug("Content received: {}", b.toString());
									}
								};
							}
						})
					.finish();
				}

				// If you need to call cancel() within received():
				{
					HttpRequestBuilder b = HttpTimeout.wrap(timeout, 1d, HttpLimit.wrap(limit, LIMIT, client.request()));
					HttpContentSender s = b.build(request);
					b.receive(new HttpReceiver() {
						@Override
						public void failed(IOException ioe) {
						}
						@Override
						public HttpContentReceiver received(HttpResponse response) {
							// Here you can call s.cancel()
							return new HttpContentReceiver() {
								private final InMemoryBuffers b = new InMemoryBuffers();
								@Override
								public void received(ByteBuffer buffer) {
									b.add(buffer);
								}
								@Override
								public void ended() {
									LOGGER.debug("Content received: {}", b.toString());
								}
							};
						}
					});
					s.finish();
				}
			}
		});
	}
	
	private static Lock<Object, IOException> getRequest(DnsConnecter dns, final HttpConnecter client, final Timeout timeout, final Limit limit, String url, final boolean keepAlive) throws IOException {
		final Lock<Object, IOException> lock = new Lock<>();
		HttpRequest.resolve(dns, url, HttpMethod.GET, keepAlive ? ImmutableMultimap.<String, String>of() : ImmutableMultimap.of(HttpHeaderKey.CONNECTION, HttpHeaderValue.CLOSE), new HttpRequest.ResolveCallback() {
			@Override
			public void failed(IOException e) {
				lock.fail(e);
			}
			
			@Override
			public void resolved(HttpRequest request) {
				HttpTimeout.wrap(timeout, 1d, HttpLimit.wrap(limit, LIMIT, client.request()))
				.build(request)
				.receive(new HttpReceiver() {
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
					})
				.finish();
			}
		});
		return lock;
	}
	
	private static void getRequest(DnsConnecter dns, HttpConnecter client, Timeout timeout, Limit limit, String url, boolean keepAlive, String expected) throws IOException {
		Assertions.assertThat(getRequest(dns, client, timeout, limit, url, keepAlive).waitFor()).isEqualTo(expected);
	}
	
	private static Lock<Object, IOException> postRequest(DnsConnecter dns, final HttpConnecter client, final Timeout timeout, final Limit limit, String url, final boolean keepAlive, final String post) throws IOException {
		final Lock<Object, IOException> lock = new Lock<>();
		HttpRequest.resolve(dns, url, HttpMethod.POST, keepAlive ? ImmutableMultimap.<String, String>of() : ImmutableMultimap.of(HttpHeaderKey.CONNECTION, HttpHeaderValue.CLOSE), new HttpRequest.ResolveCallback() {
			@Override
			public void failed(IOException e) {
				lock.fail(e);
			}
			
			@Override
			public void resolved(HttpRequest request) {
				HttpTimeout.wrap(timeout, 1d, HttpLimit.wrap(limit, LIMIT, client.request()))
				.build(request)
				.receive(new HttpReceiver() {
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
					})
				.send(ByteBuffer.wrap(post.getBytes(Charsets.UTF_8)), new Nop())
				.finish();
			}
		});
		return lock;
	}
	
	private static void postRequest(DnsConnecter dns, HttpConnecter client, Timeout timeout, Limit limit, String url, boolean keepAlive, String post, String expected) throws IOException {
		Assertions.assertThat(postRequest(dns, client, timeout, limit, url, keepAlive, post).waitFor()).isEqualTo(expected);
	}
	
	private static Disconnectable server(Ninio ninio, int port, final String suffix) {
		final Wait waitForClosing = new Wait();
		final Listener tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, port)));
		tcp.listen(HttpListening.builder().with(new SerialExecutor(HttpGetTest.class)).with(new HttpListeningHandler() {
						
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
			final Limit limit = new Limit();
			try (Timeout timeout = new Timeout()) {
				try (DnsConnecter dns = ninio.create(DnsClient.builder().with(executor)); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(executor))) {
					HttpRequest.resolve(dns, "http://google.com", new HttpRequest.ResolveCallback() {
						@Override
						public void failed(IOException e) {
							e.printStackTrace();
						}
						
						@Override
						public void resolved(HttpRequest request) {
							HttpRequestBuilder b = HttpTimeout.wrap(timeout, TIMEOUT, HttpLimit.wrap(limit, LIMIT, client.request()));
							HttpContentSender s = b.build(request);
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
						}
					});
					
					Thread.sleep(2000);
				}
			}
		}
	}
	
	@Test
	public void testSimpleGet() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (Disconnectable tcp = server(ninio, port)) {
				try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
					getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", true, "/test1");
				}
			}
		}
	}

	@Test
	public void testSimplePost() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
					postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", true, "TEST1", "TEST1");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testSimpleGetConnectionClosed() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
					getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", false, "/test1");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testDoubleGet() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
					getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", true, "/test1");
					getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test2", true, "/test2");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testDoublePost() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
					postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", true, "TEST1", "TEST1");
					postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test2", true, "TEST2", "TEST2");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testDoubleGetConnectionClosed() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
					getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", false, "/test1");
					getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test2", false, "/test2");
				}
			} finally {
				tcp.close();
			}
		}
	}

	@Test
	public void testGetServerRestarted() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
				Disconnectable tcp = server(ninio, port);
				try {
					getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", true, "/test1");
				} finally {
					tcp.close();
				}
				Thread.sleep(100);
				tcp = server(ninio, port);
				try {
					getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test2", true, "/test2");
				} finally {
					tcp.close();
				}
			}
		}
	}

	@Test
	public void testGetServerRestartedConnectionClose() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
				Disconnectable tcp = server(ninio, port);
				try {
					getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", false, "/test1");
				} finally {
					tcp.close();
				}
				Thread.sleep(100);
				tcp = server(ninio, port);
				try {
					getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test2", false, "/test2");
				} finally {
					tcp.close();
				}
			}
		}
	}

	@Test
	public void testPostServerRestarted() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
				Disconnectable tcp = server(ninio, port);
				try {
					postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", true, "TEST1", "TEST1");
				} finally {
					tcp.close();
				}
				Thread.sleep(100);
				tcp = server(ninio, port);
				try {
					postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test2", true, "TEST2", "TEST2");
				} finally {
					tcp.close();
				}
			}
		}
	}

	@Test
	public void testPostServerRestartedConnectionClose() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
				Disconnectable tcp = server(ninio, port);
				try {
					postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", false, "TEST1", "TEST1");
				} finally {
					tcp.close();
				}
				Thread.sleep(100);
				tcp = server(ninio, port);
				try {
					postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test2", false, "TEST2", "TEST2");
				} finally {
					tcp.close();
				}
			}
		}
	}

	/*%%
	@Ignore
	@Test
	public void testPostServerRestartedWhilePosting() throws Exception {
		final int port = 8080;
		Limit limit = new Limit(LIMIT);
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
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
				
				final String url = "http://" + LOCALHOST + ":" + port + "/test0";
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
					s.send(ByteBuffer.wrap(post, i, 1), new Nop());
					LOGGER.debug("WAITING");
					Thread.sleep(20);
				}
				s.finish();
				//Assertions.assertThat(lock.waitFor()).isEqualTo("TEST0_a");

				postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", true, "TEST1", "TEST1_b");
				Thread.sleep(100);
				postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test2", true, "TEST2", "TEST2_b");
				postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test3", true, "TEST3", "TEST3_b");
				Thread.sleep(100);
				postRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test4", true, "TEST4", "TEST4_c");
			}
		}
	}
	*/

	@Test
	public void testParallelGet() throws Exception {
		int port = 8080;
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).with(new SerialExecutor(HttpGetTest.class)))) {
					Lock<Object, IOException> lock1 = getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", true);
					Lock<Object, IOException> lock2 = getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test2", true);
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
		Limit limit = new Limit();
		try (Ninio ninio = Ninio.create(); Timeout timeout = new Timeout()) {
			Disconnectable tcp = server(ninio, port);
			try {
				try (DnsConnecter dns = ninio.create(DnsClient.builder().with(new SerialExecutor(HttpGetTest.class))); HttpConnecter client = ninio.create(HttpClient.builder().with(dns).pipelining().with(new SerialExecutor(HttpGetTest.class)))) {
					Lock<Object, IOException> lock1 = getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test1", true);
					Lock<Object, IOException> lock2 = getRequest(dns, client, timeout, limit, "http://" + LOCALHOST + ":" + port + "/test2", true);
					Assertions.assertThat(lock1.waitFor()).isEqualTo("/test1");
					Assertions.assertThat(lock2.waitFor()).isEqualTo("/test2");
				}
			} finally {
				tcp.close();
			}
		}
	}

}
