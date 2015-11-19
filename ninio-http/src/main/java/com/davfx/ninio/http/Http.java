package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.Failable;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.core.SocketReadyFactory;
import com.davfx.ninio.core.SslReadyFactory;
import com.davfx.ninio.core.Trust;
import com.google.common.collect.ImmutableMultimap;

public final class Http {

	private static final Queue DEFAULT_QUEUE = new Queue();

	private Queue queue = DEFAULT_QUEUE;
	private ReadyFactory readyFactory = new SocketReadyFactory();
	private ReadyFactory secureReadyFactory = new SslReadyFactory(new Trust());

	public Http() {
	}

	public Http withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	
	public Http override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}

	public Http overrideSecure(ReadyFactory secureReadyFactory) {
		this.secureReadyFactory = secureReadyFactory;
		return this;
	}

	public Http withTrust(Trust trust) {
		secureReadyFactory = new SslReadyFactory(trust);
		return this;
	}

	public HttpClient client() {
		return new HttpClient(queue, readyFactory, secureReadyFactory);
	}
	
	public static interface Handler extends Failable, Closeable {
		void handle(HttpResponse response);
		void handle(ByteBuffer buffer);
	}
	
	public void get(String url, final Handler handler) {
		final HttpClient client = client();
		client.send(HttpRequest.of(url), new HttpClientHandler() {
			@Override
			public void failed(IOException e) {
				client.close();
				handler.failed(e);
			}
			@Override
			public void received(HttpResponse response) {
				handler.handle(response);
			}
			@Override
			public void ready(CloseableByteBufferHandler write) {
			}
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				handler.handle(buffer);
			}
			@Override
			public void close() {
				client.close();
				handler.close();
			}
		});
	}
	public void post(String url, final ByteBuffer post, final Handler handler) {
		final HttpClient client = client();
		client.send(HttpRequest.of(url, HttpMethod.POST, ImmutableMultimap.<String, HttpHeaderValue>of()), new HttpClientHandler() {
			@Override
			public void failed(IOException e) {
				client.close();
				handler.failed(e);
			}
			@Override
			public void received(HttpResponse response) {
				handler.handle(response);
			}
			@Override
			public void ready(CloseableByteBufferHandler write) {
				write.handle(null, post);
				write.close();
			}
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				handler.handle(buffer);
			}
			@Override
			public void close() {
				client.close();
				handler.close();
			}
		});
	}
	
	public static interface InMemoryHandler extends Failable {
		void handle(HttpResponse response, InMemoryBuffers content);
	}
	
	public void get(String url, final InMemoryHandler handler) {
		final HttpClient client = client();
		client.send(HttpRequest.of(url), new HttpClientHandler() {
			private final InMemoryBuffers content = new InMemoryBuffers();
			private HttpResponse response;
			
			@Override
			public void failed(IOException e) {
				client.close();
				handler.failed(e);
			}
			@Override
			public void received(HttpResponse response) {
				this.response = response;
			}
			@Override
			public void ready(CloseableByteBufferHandler write) {
			}
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				content.add(buffer);
			}
			@Override
			public void close() {
				client.close();
				handler.handle(response, content);
			}
		});
	}
	public void post(String url, final ByteBuffer post, final InMemoryHandler handler) {
		final HttpClient client = client();
		client.send(HttpRequest.of(url, HttpMethod.POST, ImmutableMultimap.<String, HttpHeaderValue>of()), new HttpClientHandler() {
			private final InMemoryBuffers content = new InMemoryBuffers();
			private HttpResponse response;

			@Override
			public void failed(IOException e) {
				client.close();
				handler.failed(e);
			}
			@Override
			public void received(HttpResponse response) {
				this.response = response;
			}
			@Override
			public void ready(CloseableByteBufferHandler write) {
				write.handle(null, post);
				write.close();
			}
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				content.add(buffer);
			}
			@Override
			public void close() {
				client.close();
				handler.handle(response, content);
			}
		});
	}
}
