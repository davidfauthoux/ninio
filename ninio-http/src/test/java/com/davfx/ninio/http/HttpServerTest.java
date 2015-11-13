package com.davfx.ninio.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.util.GlobalQueue;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;

public class HttpServerTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerTest.class);
	
	@Test
	public void testGetServerWithJavaClient() throws Exception {
		try (HttpServer server = new HttpServer(GlobalQueue.get(), null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void closed() {
			}
			
			@Override
			public HttpServerHandler create() {
				return new HttpServerHandler() {
					private HttpRequest request;
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed", e);
					}
					@Override
					public void close() {
						LOGGER.debug("Closed");
					}
					
					@Override
					public void handle(HttpRequest request) {
						LOGGER.debug("Request received: {}", request);
						this.request = request;
					}

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						LOGGER.debug("Post received: {}", new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8));
					}
					
					@Override
					public void ready(Write write) {
						LOGGER.debug("Ready to write");
						write.write(new HttpResponse());
						write.handle(null, ByteBuffer.wrap(("hello:" + request.path).getBytes(Charsets.UTF_8)));
						write.close();
					}
					
				};
			}
			
		})) {
			
			Thread.sleep(100);
			
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/test?a=b").openConnection();
			StringBuilder b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("hello:/test?a=b\n");
		}
	}
	
	@Test
	public void testPostServerWithJavaClient() throws Exception {
		try (HttpServer server = new HttpServer(GlobalQueue.get(), null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void closed() {
			}
			
			@Override
			public HttpServerHandler create() {
				return new HttpServerHandler() {
					private HttpRequest request;
					private String post;
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed", e);
					}
					@Override
					public void close() {
						LOGGER.debug("Closed");
					}
					
					@Override
					public void handle(HttpRequest request) {
						LOGGER.debug("Request received: {}", request);
						this.request = request;
					}

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						post = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Post received: {}", post);
					}
					
					@Override
					public void ready(Write write) {
						LOGGER.debug("Ready to write");
						write.write(new HttpResponse());
						write.handle(null, ByteBuffer.wrap(("hello:" + request.path + ":" + post).getBytes(Charsets.UTF_8)));
						write.close();
					}
					
				};
			}
			
		})) {
			
			Thread.sleep(100);
			
			HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:8080/test?a=b").openConnection();
			c.setDoOutput(true);
			try (Writer w = new OutputStreamWriter(c.getOutputStream())) {
				w.write("post");
			}
			StringBuilder b = new StringBuilder();
			try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), Charsets.UTF_8))) {
				while (true) {
					String line = r.readLine();
					if (line == null) {
						break;
					}
					b.append(line).append('\n');
				}
			}
			c.disconnect();
			Assertions.assertThat(b.toString()).isEqualTo("hello:/test?a=b:post\n");
		}
	}
	
	@Test
	public void testGetServerWithNinioClient() throws Exception {
		try (HttpServer server = new HttpServer(GlobalQueue.get(), null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void closed() {
			}
			
			@Override
			public HttpServerHandler create() {
				return new HttpServerHandler() {
					private HttpRequest request;
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed", e);
					}
					@Override
					public void close() {
						LOGGER.debug("Closed");
					}
					
					@Override
					public void handle(HttpRequest request) {
						LOGGER.debug("Request received: {}", request);
						this.request = request;
					}

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						LOGGER.debug("Post received: {}", new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8));
					}
					
					@Override
					public void ready(Write write) {
						LOGGER.debug("Ready to write response");
						write.write(new HttpResponse());
						write.handle(null, ByteBuffer.wrap(("hello:" + request.path).getBytes(Charsets.UTF_8)));
						write.close();
					}
					
				};
			}
			
		})) {
			
			Thread.sleep(100);
			
			final Lock<String, IOException> lock = new Lock<>();
			new Http().client().send(new HttpRequest(new Address(Address.LOCALHOST, 8080), false, HttpMethod.GET, "/test?a=b"), new HttpClientHandler() {
				private HttpResponse response;
				
				@Override
				public void failed(IOException e) {
					lock.fail(e);
				}
				
				@Override
				public void ready(CloseableByteBufferHandler write) {
					LOGGER.debug("Ready to write post");
				}
				
				@Override
				public void received(HttpResponse response) {
					LOGGER.debug("Response received: {}", response);
					this.response = response;
				}

				@Override
				public void handle(Address address, ByteBuffer buffer) {
					String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
					LOGGER.debug("Received: {}", s);
					lock.set(response.status + ":" + response.reason + ":" + s);
				}
				
				@Override
				public void close() {
				}
			});
			
			Assertions.assertThat(lock.waitFor()).isEqualTo("200:OK:hello:/test?a=b");
		}
	}
	

	@Test
	public void testPostServerWithNinioClient() throws Exception {
		try (HttpServer server = new HttpServer(GlobalQueue.get(), null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
			@Override
			public void failed(IOException e) {
			}
			@Override
			public void closed() {
			}
			
			@Override
			public HttpServerHandler create() {
				return new HttpServerHandler() {
					private HttpRequest request;
					private String post;
					
					@Override
					public void failed(IOException e) {
						LOGGER.warn("Failed", e);
					}
					@Override
					public void close() {
						LOGGER.debug("Closed");
					}
					
					@Override
					public void handle(HttpRequest request) {
						LOGGER.debug("Request received: {}", request);
						this.request = request;
					}

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						post = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Post received: {}", post);
					}
					
					@Override
					public void ready(Write write) {
						LOGGER.debug("Ready to write");
						write.write(new HttpResponse());
						write.handle(null, ByteBuffer.wrap(("hello:" + request.path + ":" + post).getBytes(Charsets.UTF_8)));
						write.close();
					}
					
				};
			}
			
		})) {
			
			Thread.sleep(100);
			
			final Lock<String, IOException> lock = new Lock<>();
			final String post = "post";
			try (HttpClient client = new Http().client()) {
				client.send(new HttpRequest(new Address(Address.LOCALHOST, 8080), false, HttpMethod.POST, "/test?a=b", ImmutableMultimap.of(HttpHeaderKey.CONTENT_LENGTH, String.valueOf(post.length()))), new HttpClientHandler() {
					private HttpResponse response;
					
					@Override
					public void failed(IOException e) {
						lock.fail(e);
					}
					
					@Override
					public void ready(CloseableByteBufferHandler write) {
						LOGGER.debug("Ready to write post");
						write.handle(null, ByteBuffer.wrap(post.getBytes(Charsets.UTF_8)));
					}
					
					@Override
					public void received(HttpResponse response) {
						LOGGER.debug("Response received: {}", response);
						this.response = response;
					}
	
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
						LOGGER.debug("Received: {}", s);
						lock.set(response.status + ":" + response.reason + ":" + s);
					}
					
					@Override
					public void close() {
					}
				});
			
				Assertions.assertThat(lock.waitFor()).isEqualTo("200:OK:hello:/test?a=b:" + post);
			}
		}
	}
}
