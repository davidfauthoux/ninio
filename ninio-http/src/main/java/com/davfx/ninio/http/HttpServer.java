package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.Listen;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.QueueListen;
import com.davfx.ninio.core.SocketListen;
import com.davfx.ninio.core.SocketListening;
import com.davfx.ninio.core.SslSocketListening;
import com.davfx.ninio.core.Trust;

public final class HttpServer implements AutoCloseable, Closeable {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpServer.class);
	
	private final Queue queue;
	private SocketListening.Listening listening = null;
	private boolean closed = false;
	
	public HttpServer(final Queue queue, final Trust trust, final Address address, final HttpServerHandlerFactory factory) {
		this.queue = queue;

		LOGGER.trace("Creating http server");

		SocketListening listening = new SocketListening() {
			@Override
			public void listening(Listening listening) {
				if (closed) {
					LOGGER.trace("Http server internally closed");
					listening.disconnect();
					listening.close();
				} else {
					LOGGER.trace("Http server created");
					HttpServer.this.listening = listening;
				}
			}
			
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
				return new HttpRequestReader(address, trust != null, h, connection);
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
		
		if (trust != null) {
			listening = new SslSocketListening(trust, queue.allocator(), listening);
		}
		
		Listen listen = new SocketListen(queue.getSelector(), queue.allocator());
		listen = new QueueListen(queue, listen);
		listen.listen(address, listening);
	}
	
	@Override
	public void close() {
		LOGGER.trace("Closing http server");
		queue.post(new Runnable() {
			@Override
			public void run() {
				closed = true;
				if (listening != null) {
					LOGGER.trace("Http server closed");
					listening.disconnect();
					listening.close();
				}
			}
		});
	}
}
