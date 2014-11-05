package com.davfx.ninio.http.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ByteBufferHandler;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Trust;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpClientHandler;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;

public final class SimpleHttpClient {
	private static final Logger LOGGER = LoggerFactory.getLogger(SimpleHttpClient.class);
	
	private Queue queue = null;
	private Trust trust = null;
	private Address address = new Address("localhost", Http.DEFAULT_PORT);
	private String host = null;
	private int port = -1;
	private boolean secure = false;
	private String path = "/";
	private HttpRequest.Method method = HttpRequest.Method.GET;
	private String postContentType = null;
	private ByteBuffer post = null;
	
	private HttpClient client = null;
	
	public SimpleHttpClient() {
	}
	
	public SimpleHttpClient on(HttpClient client) {
		this.client = client;
		return this;
	}

	public SimpleHttpClient withTrust(Trust trust) {
		this.trust = trust;
		secure = true;
		return this;
	}
	public SimpleHttpClient withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	public SimpleHttpClient withAddress(Address address) {
		this.address = address;
		return this;
	}
	public SimpleHttpClient withHost(String host) {
		this.host = host;
		return this;
	}
	public SimpleHttpClient withPort(int port) {
		this.port = port;
		return this;
	}
	public SimpleHttpClient secure(boolean secure) {
		this.secure = secure;
		return this;
	}
	public SimpleHttpClient on(String path) {
		this.path = path;
		return this;
	}
	public SimpleHttpClient withMethod(HttpRequest.Method method) {
		this.method = method;
		return this;
	}
	public SimpleHttpClient post(String contentType, ByteBuffer post) {
		postContentType = contentType;
		this.post = post;
		return this;
	}
	
	public static interface Finished extends AutoCloseable {
		Finished send(SimpleHttpClientHandler handler);
	}
	
	public AutoCloseable send(SimpleHttpClientHandler handler) {
		return finish().send(handler);
	}
	
	public Finished finish() {
		final HttpClient onClient;
		final boolean shouldCloseClient;
		final Queue q;
		final boolean shouldCloseQueue;
		if (client == null) {
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
			onClient = new HttpClient(q, trust);
			shouldCloseClient = true;
		} else {
			onClient = client;
			shouldCloseClient = false;
			q = null;
			shouldCloseQueue = false;
		}
		
		return new Finished() {
			@Override
			public void close() {
				if (shouldCloseQueue) {
					q.close();
				}
				if (shouldCloseClient) {
					onClient.close();
				}
			}
			
			@Override
			public Finished send(final SimpleHttpClientHandler handler) {
				final Address a;
				if (host != null) {
					if (port < 0) {
						a = new Address(host, secure ? Http.DEFAULT_SECURE_PORT : Http.DEFAULT_PORT);
					} else {
						a = new Address(host, port);
					}
				} else {
					a = address;
				}
				
				if (post != null) {
					method = HttpRequest.Method.POST;
				}
				HttpRequest request = new HttpRequest(a, secure, method, path);
				if (post != null) {
					request.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(post.remaining()));
					if (postContentType != null) {
						request.getHeaders().put(Http.CONTENT_TYPE, postContentType);
					}
				}
				onClient.send(request, new HttpClientHandler() {
					private HttpResponse response = null;
					private InMemoryPost body = null;
					
					@Override
					public void ready(ByteBufferHandler write) {
						if (post == null) {
							return;
						}
						write.handle(null, post);
					}
					
					@Override
					public void received(HttpResponse response) {
						this.response = response;
						body = new InMemoryPost();
					}

					@Override
					public void handle(Address address, ByteBuffer buffer) {
						body.add(buffer);
					}
					
					@Override
					public void close() {
						if (response == null) {
							LOGGER.error("Closed before any response received");
							handler.handle(-1, null, null);
							return;
						}
						handler.handle(response.getStatus(), response.getReason(), body);
					}
					
					@Override
					public void failed(IOException e) {
						LOGGER.error("Failed to connect to: {}", a, e);
						handler.handle(-1, null, null);
					}
				});
				
				return this;
			}
		};
	}

}
