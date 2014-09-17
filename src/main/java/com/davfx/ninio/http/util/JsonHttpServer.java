package com.davfx.ninio.http.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Trust;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;
import com.google.gson.JsonElement;

public final class JsonHttpServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonHttpServer.class);

	private Queue queue = null;
	private Trust trust = null;
	private Address address = new Address("localhost", Http.DEFAULT_PORT);
	private int port = -1;
	
	public JsonHttpServer() {
	}
	
	public JsonHttpServer withTrust(Trust trust) {
		this.trust = trust;
		return this;
	}
	public JsonHttpServer withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	public JsonHttpServer withAddress(Address address) {
		this.address = address;
		return this;
	}
	public JsonHttpServer withPort(int port) {
		this.port = port;
		return this;
	}
	
	public AutoCloseable start(JsonHttpServerHandler handler) {
		Queue q;
		boolean shouldCloseQueue;
		if (queue == null) {
			try {
				q = new Queue();
			} catch (IOException e) {
				LOGGER.error("Queue could not be created", e);
				return null;
			}
			shouldCloseQueue = true;
		} else {
			q = queue;
			shouldCloseQueue = false;
		}

		Address a = address;
		if (port >= 0) {
			a = new Address(port);
		}
		
		new HttpServer(q, trust, a, new HttpServerHandlerFactory() {
			@Override
			public HttpServerHandler create() {
				return new HttpServerHandler() {
					private HttpRequest request = null;
					private InMemoryPost post = null;
					
					@Override
					public void handle(HttpRequest request) {
						this.request = request;
					}

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						if (post == null) {
							post = new InMemoryPost();
						}
						post.add(buffer);
					}
					
					@Override
					public void ready(Write write) {
						HttpQuery query = new HttpQuery(request.getPath());
						
						String path = query.getPath();
						Parameters parameters = query.getParameters();
						
						JsonHttpServerHandler.Callback callback = new JsonHttpServerHandler.Callback() {
							@Override
							public void send(JsonElement response) {
								if (response == null) {
									HttpResponse r = new HttpResponse(Http.Status.INTERNAL_SERVER_ERROR, Http.Message.INTERNAL_SERVER_ERROR);
									write.write(r);
									write.close();
									return;
								} else {
									HttpResponse r = new HttpResponse(Http.Status.OK, Http.Message.OK);
									r.getHeaders().put(Http.CONTENT_TYPE, Http.ContentType.JSON);
									ByteBuffer bb = ByteBuffer.wrap(response.toString().getBytes(Http.UTF8_CHARSET));
									r.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(bb.remaining()));
									write.write(r);
									write.handle(null, bb);
									write.close();
								}
							}
						};
						
						switch (request.getMethod()) {
						case GET:
							handler.get(path, parameters, callback);
							break;
						case DELETE:
							handler.delete(path, parameters, callback);
							break;
						case HEAD:
							handler.head(path, parameters, callback);
							break;
						case PUT:
							handler.put(path, parameters, callback);
							break;
						case POST:
							handler.post(path, parameters, post, callback);
							break;
						default:
							HttpResponse r = new HttpResponse(Http.Status.INTERNAL_SERVER_ERROR, Http.Message.INTERNAL_SERVER_ERROR);
							write.write(r);
							write.close();
							break;
						}
						request = null;
						post = null;
					}
					
					@Override
					public void failed(IOException e) {
						LOGGER.debug("Client connection failed", e);
					}
					
					@Override
					public void close() {
						LOGGER.debug("Connection closed by peer");
					}
				};
			}
			
			@Override
			public void closed() {
				if (shouldCloseQueue) {
					q.close();
				}
				LOGGER.debug("Server closed");
			}

			@Override
			public void failed(IOException e) {
				if (shouldCloseQueue) {
					q.close();
				}
				LOGGER.error("Server could not be launched", e);
			}
		});
		
		return new AutoCloseable() {
			@Override
			public void close() {
				if (shouldCloseQueue) {
					q.close();
				}
			}
		};
	}
}
