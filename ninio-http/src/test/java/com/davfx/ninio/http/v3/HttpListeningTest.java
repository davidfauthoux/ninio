package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.Ninio;
import com.davfx.ninio.core.v3.TcpSocketServer;
import com.google.common.base.Charsets;

public class HttpListeningTest {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(HttpListeningTest.class);
	
	@Test
	public void testPostServerWithJavaClient() throws Exception {
		try (Ninio ninio = Ninio.create()) {
			Disconnectable tcp = ninio.create(TcpSocketServer.builder().bind(new Address(Address.ANY, 8080)).listening(HttpListening.builder().with(Executors.newSingleThreadExecutor()).with(new HttpListeningHandler() {
				@Override
				public ConnectionHandler create() {
					return new ConnectionHandler() {
						@Override
						public HttpContentReceiver handle(HttpRequest request, final ResponseHandler responseHandler) {
							return new HttpContentReceiver() {
								@Override
								public void received(ByteBuffer buffer) {
								}
								@Override
								public void ended() {
									HttpContentSender sender = responseHandler.send(HttpResponse.ok());
									sender.send(ByteBuffer.wrap("Hello".getBytes(Charsets.UTF_8))).finish();
								}
							};
						}
						@Override
						public void closed() {
						}
					};
				}
			}).build()));
			try {
				Thread.sleep(100000);
			} finally {
				tcp.close();
			}
		}
		/*
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
						private com.davfx.ninio.http.HttpRequest request;
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
						public void handle(com.davfx.ninio.http.HttpRequest request) {
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

				try (HttpClient client = ninio.create(HttpClient.builder().with(Executors.newSingleThreadExecutor()))) {
					{
						client.request()
						.failing(new Failing() {
							@Override
							public void failed(IOException e) {
								LOGGER.error("Failed", e);
							}
						})
						.receiving(new HttpReceiver() {
							@Override
							public ContentReceiver received(Disconnectable disconnectable, HttpResponse response) {
								LOGGER.debug("RESPONSE {}", response);
								return new ContentReceiver() {
									@Override
									public void received(ByteBuffer buffer) {
										LOGGER.debug("Received {}", new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
									}
									@Override
									public void ended() {
										LOGGER.debug("ENDED");
									}
								};
							}
						})
						.build()
						.create(HttpRequest.of("http://127.0.0.1:8080/test?a=b")).finish();
					}		
					Thread.sleep(50);
					{
						client.request()
						.failing(new Failing() {
							@Override
							public void failed(IOException e) {
								LOGGER.error("Failed", e);
							}
						})
						.receiving(new HttpReceiver() {
							@Override
							public ContentReceiver received(Disconnectable disconnectable, HttpResponse response) {
								LOGGER.debug("RESPONSE {}", response);
								return new ContentReceiver() {
									@Override
									public void received(ByteBuffer buffer) {
										LOGGER.debug("Received {}", new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
									}
									@Override
									public void ended() {
										LOGGER.debug("ENDED");
									}
								};
							}
						})
						.build()
						.create(HttpRequest.of("http://127.0.0.1:8080/test?a=b", HttpMethod.POST, ImmutableMultimap.of(HttpHeaderKey.CONTENT_LENGTH, "4"))).post(ByteBuffer.wrap("post".getBytes(Charsets.UTF_8))).finish();
					}
					{
						client.request()
						.failing(new Failing() {
							@Override
							public void failed(IOException e) {
								LOGGER.error("Failed", e);
							}
						})
						.receiving(new HttpReceiver() {
							@Override
							public ContentReceiver received(Disconnectable disconnectable, HttpResponse response) {
								LOGGER.debug("RESPONSE {}", response);
								return new ContentReceiver() {
									@Override
									public void received(ByteBuffer buffer) {
										LOGGER.debug("Received {}", new String(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining()));
									}
									@Override
									public void ended() {
										LOGGER.debug("ENDED");
									}
								};
							}
						})
						.build()
						.create(HttpRequest.of("http://127.0.0.1:8080/test?a=b", HttpMethod.POST, ImmutableMultimap.of(HttpHeaderKey.CONTENT_LENGTH, "4"))).post(ByteBuffer.wrap("post".getBytes(Charsets.UTF_8))).finish();
					}
					Thread.sleep(10000);
				}
				}

			queue.finish().waitFor();
		}
		}*/
	}

}