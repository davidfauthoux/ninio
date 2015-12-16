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

public final class Http implements AutoCloseable, Closeable {

	private static final Queue DEFAULT_QUEUE = new Queue();

	private Queue queue = DEFAULT_QUEUE;
	private ReadyFactory readyFactory = null;
	private ReadyFactory secureReadyFactory = null;
	private Trust trust = new Trust();
	private HttpRecycle recycle;

	public Http() {
		recycle = new HttpRecycle(queue, (readyFactory == null) ? new SocketReadyFactory(queue) : readyFactory, (secureReadyFactory == null) ? new SslReadyFactory(queue, trust) : secureReadyFactory);
	}
	
	@Override
	public void close() {
		recycle.close();
	}

	private void createRecycle() {
		recycle.close();
		recycle = new HttpRecycle(queue, (readyFactory == null) ? new SocketReadyFactory(queue) : readyFactory, (secureReadyFactory == null) ? new SslReadyFactory(queue, trust) : secureReadyFactory);
	}
	
	public Http withQueue(Queue queue) {
		this.queue = queue;
		createRecycle();
		return this;
	}
	
	public Http override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		createRecycle();
		return this;
	}

	public Http overrideSecure(ReadyFactory secureReadyFactory) {
		this.secureReadyFactory = secureReadyFactory;
		createRecycle();
		return this;
	}

	public Http withTrust(Trust trust) {
		this.trust = trust;
		createRecycle();
		return this;
	}
	
	public HttpClient client() {
		return new HttpClient(queue, recycle);
	}

	public static interface Handler extends Failable, Closeable {
		void handle(HttpResponse response);
		void handle(ByteBuffer buffer);
	}
	
	private static HttpRequest addContentLength(HttpRequest r, ByteBuffer post) {
		if (post == null) {
			return r;
		}
		ImmutableMultimap.Builder<String, HttpHeaderValue> headers = ImmutableMultimap.builder();
		headers.putAll(r.headers);
		headers.put(HttpHeaderKey.CONTENT_LENGTH, HttpHeaderValue.simple(String.valueOf(post.remaining())));
		return new HttpRequest(r.address, r.secure, r.method, r.path, headers.build());
	}
	
	public void send(HttpRequest r, final ByteBuffer post, final Handler handler) {
		final HttpClient client = client();
		client.send(addContentLength(r, post), new HttpClientHandler() {
			@Override
			public void failed(IOException e) {
				handler.failed(e);
			}
			@Override
			public void received(HttpResponse response) {
				handler.handle(response);
			}
			@Override
			public void ready(CloseableByteBufferHandler write) {
				if (post != null) {
					write.handle(null, post);
				}
				write.close();
			}
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				handler.handle(buffer);
			}
			@Override
			public void close() {
				handler.close();
			}
		});
	}
	public void get(String url, Handler handler) {
		send(HttpRequest.of(url), null, handler);
	}
	public void post(String url, ByteBuffer post, Handler handler) {
		send(HttpRequest.of(url, HttpMethod.POST, ImmutableMultimap.<String, HttpHeaderValue>of()), post, handler);
	}
	
	public static interface InMemoryHandler extends Failable {
		void handle(HttpResponse response, InMemoryBuffers content);
	}
	
	public void send(HttpRequest r, final ByteBuffer post, final InMemoryHandler handler) {
		final HttpClient client = client();
		client.send(addContentLength(r, post), new HttpClientHandler() {
			private final InMemoryBuffers content = new InMemoryBuffers();
			private HttpResponse response;

			@Override
			public void failed(IOException e) {
				handler.failed(e);
			}
			@Override
			public void received(HttpResponse response) {
				this.response = response;
			}
			@Override
			public void ready(CloseableByteBufferHandler write) {
				if (post != null) {
					write.handle(null, post);
				}
				write.close();
			}
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				content.add(buffer);
			}
			@Override
			public void close() {
				handler.handle(response, content);
			}
		});
	}
	public void get(String url, InMemoryHandler handler) {
		send(HttpRequest.of(url), null, handler);
	}
	public void post(String url, ByteBuffer post, InMemoryHandler handler) {
		send(HttpRequest.of(url, HttpMethod.POST, ImmutableMultimap.<String, HttpHeaderValue>of()), post, handler);
	}
}
