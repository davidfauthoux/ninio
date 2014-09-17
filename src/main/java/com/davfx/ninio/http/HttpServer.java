package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.Listen;
import com.davfx.ninio.common.OnceByteBufferAllocator;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.QueueListen;
import com.davfx.ninio.common.SocketListen;
import com.davfx.ninio.common.SocketListening;
import com.davfx.ninio.common.SslSocketListening;
import com.davfx.ninio.common.Trust;

public final class HttpServer {
	public HttpServer(Queue queue, Address serverAddress, HttpServerHandlerFactory factory) {
		this(queue, null, serverAddress, factory);
	}
	public HttpServer(Queue queue, final Trust trust, Address serverAddress, final HttpServerHandlerFactory factory) {
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
			listening = new SslSocketListening(trust, listening);
		}
		
		Listen listen = new SocketListen(queue.getSelector(), new OnceByteBufferAllocator());
		listen = new QueueListen(queue, listen);
		listen.listen(serverAddress, listening);
	}
}
