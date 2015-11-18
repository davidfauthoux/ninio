package com.davfx.ninio.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.util.Lock;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;

public class HttpServerTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServerTest.class);
	
	static {
		System.setProperty("http.keepAlive", "false");
	}
	
	@Test
	public void testGetServerWithJavaClient() throws Exception {
		try (Queue queue = new Queue()) {
			try (HttpServer server = new HttpServer(queue, null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
				@Override
				public void failed(IOException e) {
					LOGGER.error("Failed", e);
				}
				@Override
				public void closed() {
				}
				
				@Override
				public HttpServerHandler create() {
					return new HttpServerHandler() {
						private HttpRequest request;
						private final InMemoryBuffers b = new InMemoryBuffers();

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
							b.add(buffer);
						}
						
						@Override
						public void ready(Write write) {
							LOGGER.debug("Post received: {}", b.toString());
							LOGGER.debug("Ready to write");
							write.write(new HttpResponse());
							write.handle(null, ByteBuffer.wrap(("hello:" + request.path).getBytes(Charsets.UTF_8)));
							write.close();
						}
						
					};
				}
				
			})) {
				
				queue.finish().waitFor();
				
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
			
			queue.finish().waitFor();
		}
	}
	
	@Test
	public void testPostServerWithJavaClient() throws Exception {
		try (Queue queue = new Queue()) {
			try (HttpServer server = new HttpServer(queue, null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
				@Override
				public void failed(IOException e) {
					LOGGER.error("Failed", e);
				}
				@Override
				public void closed() {
				}
				
				@Override
				public HttpServerHandler create() {
					return new HttpServerHandler() {
						private HttpRequest request;
						private final InMemoryBuffers b = new InMemoryBuffers();
						
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
							b.add(buffer);
						}
						
						@Override
						public void ready(Write write) {
							String post = b.toString();
							LOGGER.debug("Post received: {}", post);
							LOGGER.debug("Ready to write");
							write.write(new HttpResponse());
							write.handle(null, ByteBuffer.wrap(("hello:" + request.path + ":" + post).getBytes(Charsets.UTF_8)));
							write.close();
						}
						
					};
				}
				
			})) {
				
				queue.finish().waitFor();
				
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

			queue.finish().waitFor();
		}
	}
	
	@Test
	public void testGetServerWithNinioClient() throws Exception {
		try (Queue queue = new Queue()) {
			try (HttpServer server = new HttpServer(queue, null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
				@Override
				public void failed(IOException e) {
					LOGGER.error("Failed", e);
				}
				@Override
				public void closed() {
				}
				
				@Override
				public HttpServerHandler create() {
					return new HttpServerHandler() {
						private HttpRequest request;
						private final InMemoryBuffers b = new InMemoryBuffers();
						
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
							b.add(buffer);
						}
						
						@Override
						public void ready(Write write) {
							LOGGER.debug("Post received: {}", b.toString());
							LOGGER.debug("Ready to write response");
							write.write(new HttpResponse());
							write.handle(null, ByteBuffer.wrap(("hello:" + request.path).getBytes(Charsets.UTF_8)));
							write.close();
						}
						
					};
				}
				
			})) {
				
				queue.finish().waitFor();
				
				final Lock<String, IOException> lock = new Lock<>();
				new Http().client().send(new HttpRequest(new Address(Address.LOCALHOST, 8080), false, HttpMethod.GET, new HttpPath("/test?a=b")), new HttpClientHandler() {
					private HttpResponse response;
					private final InMemoryBuffers b = new InMemoryBuffers();

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
						b.add(buffer);
					}
					
					@Override
					public void close() {
						String s = b.toString();
						LOGGER.debug("Received: {}", s);
						lock.set(response.status + ":" + response.reason + ":" + s);
					}
				});
				
				Assertions.assertThat(lock.waitFor()).isEqualTo("200:OK:hello:/test?a=b");
			}

			queue.finish().waitFor();
		}
	}
	

	@Test
	public void testPostServerWithNinioClient() throws Exception {
		try (Queue queue = new Queue()) {
			try (HttpServer server = new HttpServer(queue, null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
				@Override
				public void failed(IOException e) {
					LOGGER.error("Failed", e);
				}
				@Override
				public void closed() {
				}
				
				@Override
				public HttpServerHandler create() {
					return new HttpServerHandler() {
						private HttpRequest request;
						private final InMemoryBuffers b = new InMemoryBuffers();
						
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
							b.add(buffer);
						}
						
						@Override
						public void ready(Write write) {
							String post = b.toString();
							LOGGER.debug("Post received: {}", post);
							LOGGER.debug("Ready to write");
							write.write(new HttpResponse());
							write.handle(null, ByteBuffer.wrap(("hello:" + request.path + ":" + post).getBytes(Charsets.UTF_8)));
							write.close();
						}
						
					};
				}
				
			})) {
				
				queue.finish().waitFor();
				
				final Lock<String, IOException> lock = new Lock<>();
				final String post = "post";
				try (HttpClient client = new Http().client()) {
					client.send(new HttpRequest(new Address(Address.LOCALHOST, 8080), false, HttpMethod.POST, new HttpPath("/test?a=b"), ImmutableMultimap.of(HttpHeaderKey.CONTENT_LENGTH, String.valueOf(post.length()))), new HttpClientHandler() {
						private HttpResponse response;
						private final InMemoryBuffers b = new InMemoryBuffers();

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
							b.add(buffer);
						}
						
						@Override
						public void close() {
							String s = b.toString();
							LOGGER.debug("Received: {}", s);
							lock.set(response.status + ":" + response.reason + ":" + s);
						}
					});
				
					Assertions.assertThat(lock.waitFor()).isEqualTo("200:OK:hello:/test?a=b:" + post);
				}
			}

			queue.finish().waitFor();
		}
	}
	
	
	@Test
	public void testGetServerWithSimpleNinioClient() throws Exception {
		try (Queue queue = new Queue()) {
			try (HttpServer server = new HttpServer(queue, null, new Address(Address.ANY, 8080), new HttpServerHandlerFactory() {
				@Override
				public void failed(IOException e) {
					LOGGER.error("Failed", e);
				}
				@Override
				public void closed() {
				}
				
				@Override
				public HttpServerHandler create() {
					return new HttpServerHandler() {
						private HttpRequest request;
						private final InMemoryBuffers b = new InMemoryBuffers();

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
							b.add(buffer);
						}
						
						@Override
						public void ready(Write write) {
							LOGGER.debug("Post received: {}", b.toString());
							LOGGER.debug("Ready to write response");
							write.write(new HttpResponse());
							write.handle(null, ByteBuffer.wrap(("hello:" + request.path).getBytes(Charsets.UTF_8)));
							write.close();
						}
						
					};
				}
				
			})) {
				
				queue.finish().waitFor();
				
				final Lock<String, IOException> lock = new Lock<>();
				new Http().get("http://127.0.0.1:8080/test?a=b", new Http.Handler() {
					private HttpResponse response;
					private final InMemoryBuffers b = new InMemoryBuffers();
					
					@Override
					public void failed(IOException e) {
						lock.fail(e);
					}
					
					@Override
					public void handle(HttpResponse response) {
						LOGGER.debug("Response received: {}", response);
						this.response = response;
					}
	
					@Override
					public void handle(ByteBuffer buffer) {
						b.add(buffer);
					}
					
					@Override
					public void close() {
						String s = b.toString();
						LOGGER.debug("Received: {}", s);
						lock.set(response.status + ":" + response.reason + ":" + s);
					}
				});
				
				Assertions.assertThat(lock.waitFor()).isEqualTo("200:OK:hello:/test?a=b");
			}

			queue.finish().waitFor();
		}
	}

	private static final class InMemoryBuffers implements Iterable<ByteBuffer> {
		private final Deque<ByteBuffer> buffers = new LinkedList<>();
		
		public InMemoryBuffers() {
		}
		
		@Override
		public Iterator<ByteBuffer> iterator() {
			return buffers.iterator();
		}
		
		public void add(ByteBuffer buffer) {
			if (!buffer.hasRemaining()) {
				return;
			}
			buffers.addLast(buffer);
		}
		
		public int getSize() {
			int l = 0;
			for (ByteBuffer b : buffers) {
				l += b.remaining();
			}
			return l;
		}
		
		public byte[] toByteArray() {
			byte[] b = new byte[getSize()];
			int off = 0;
			for (ByteBuffer bb : buffers) {
				int pos = bb.position();
				int r = bb.remaining();
				bb.get(b, off, bb.remaining());
				off += r;
				bb.position(pos);
			}
			return b;
		}
		
		@Override
		public String toString() {
			byte[] b = toByteArray();
			return new String(b, 0, b.length, Charsets.UTF_8);
		}
	}
}
