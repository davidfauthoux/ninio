package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.Listen;
import com.davfx.ninio.common.QueueListen;
import com.davfx.ninio.common.SocketListen;
import com.davfx.ninio.common.SocketListening;
import com.davfx.ninio.common.SslSocketListening;

public final class HttpServer {
	public HttpServer(final HttpServerConfigurator configurator, final HttpServerHandlerFactory factory) {
		SocketListening listening = new SocketListening() {
			@Override
			public CloseableByteBufferHandler connected(Address address, CloseableByteBufferHandler connection) {
				HttpServerHandler h = factory.create();
				if (h == null) {
					h = new HttpServerHandler() {
						@Override
						public void ready(Write write) {
						}
						@Override
						public void handle(Address address, ByteBuffer buffer) {
						}
						@Override
						public void handle(HttpRequest request) {
						}
						@Override
						public void failed(IOException e) {
						}
						@Override
						public void close() {
						}
					};
				}
				return new HttpRequestReader(address, configurator.trust != null, h, connection);
			}
			
			@Override
			public void failed(IOException e) {
				factory.failed(e);
			}
			
			@Override
			public void close() {
				factory.closed();
			}
		};
		
		if (configurator.trust != null) {
			listening = new SslSocketListening(configurator.trust, configurator.queue.allocator(), listening);
		}
		
		Listen listen = new SocketListen(configurator.queue.getSelector(), configurator.queue.allocator());
		listen = new QueueListen(configurator.queue, listen);
		listen.listen(configurator.address, listening);
	}
}
