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
import com.davfx.ninio.util.GlobalQueue;

public final class Http {

	private Queue queue = null;
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
		Queue q = queue;
		if (q == null) {
			q = GlobalQueue.get();
		}
		return new HttpClient(q, readyFactory, secureReadyFactory);
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
			public void close() {
				client.close();
				handler.close();
			}
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				handler.handle(buffer);
			}
			@Override
			public void received(HttpResponse response) {
				handler.handle(response);
			}
			
			@Override
			public void ready(CloseableByteBufferHandler write) {
			}
		});
	}
}
