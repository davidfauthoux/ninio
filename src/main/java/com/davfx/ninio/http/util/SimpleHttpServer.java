package com.davfx.ninio.http.util;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Trust;
import com.davfx.ninio.http.Http;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;

public final class SimpleHttpServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(SimpleHttpServer.class);

	private Queue queue = null;
	private Trust trust = null;
	private Address address = new Address("localhost", Http.DEFAULT_PORT);
	private int port = -1;
	
	public SimpleHttpServer() {
	}
	
	public SimpleHttpServer withTrust(Trust trust) {
		this.trust = trust;
		return this;
	}
	public SimpleHttpServer withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	public SimpleHttpServer withAddress(Address address) {
		this.address = address;
		return this;
	}
	public SimpleHttpServer withPort(int port) {
		this.port = port;
		return this;
	}
	
	public AutoCloseable start(SimpleHttpServerHandler handler) {
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
				return new HttpServerHandlerToSimpleHttpServerHandler(handler);
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
