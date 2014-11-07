package com.davfx.ninio.http.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ByteBufferHandler;
import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpClient;
import com.davfx.ninio.http.HttpClientConfigurator;
import com.davfx.ninio.http.HttpClientHandler;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpResponse;

public final class SimpleHttpClient implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SimpleHttpClient.class);
	
	private final HttpClientConfigurator configurator;
	private final boolean configuratorToClose;
	private final HttpClient client;

	private boolean secure = false;
	private String path = "/";
	private HttpRequest.Method method = HttpRequest.Method.GET;
	private String postContentType = null;
	private ByteBuffer post = null;

	private Address address; // Overrides configurator

	private SimpleHttpClient(HttpClientConfigurator configurator, boolean configuratorToClose) {
		this.configurator = configurator;
		this.configuratorToClose = configuratorToClose;
		client = new HttpClient(configurator);
		address = configurator.address;
	}
	
	public SimpleHttpClient(HttpClientConfigurator configurator) {
		this(configurator, false);
	}
	public SimpleHttpClient() throws IOException {
		this(new HttpClientConfigurator(), true);
	}
	
	@Override
	public void close() {
		client.close();
		if (configuratorToClose) {
			configurator.close();
		}
	}
	
	public SimpleHttpClient withHost(String host) {
		address = new Address(host, address.getPort());
		return this;
	}
	public SimpleHttpClient withPort(int port) {
		address = new Address(address.getHost(), port);
		return this;
	}
	public SimpleHttpClient secure(boolean secure) {
		if (secure) {
			address = new Address(address.getHost(), Http.DEFAULT_SECURE_PORT);
		} else {
			address = new Address(address.getHost(), Http.DEFAULT_PORT);
		}
		return this;
	}
	public SimpleHttpClient withAddress(Address address) {
		this.address = address;
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
	
	public void send(final SimpleHttpClientHandler handler) {
		if (post != null) {
			method = HttpRequest.Method.POST;
		}
		HttpRequest request = new HttpRequest(address, secure, method, path);
		if (post != null) {
			request.getHeaders().put(Http.CONTENT_LENGTH, String.valueOf(post.remaining()));
			if (postContentType != null) {
				request.getHeaders().put(Http.CONTENT_TYPE, postContentType);
			}
		}
		client.send(request, new HttpClientHandler() {
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
				LOGGER.error("Failed to connect to: {}", configurator.address, e);
				handler.handle(-1, null, null);
			}
		});
	}

}
