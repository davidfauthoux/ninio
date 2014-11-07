package com.davfx.ninio.http.util;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerConfigurator;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;

public final class SimpleHttpServer implements Closeable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SimpleHttpServer.class);

	private final HttpServerConfigurator configurator;
	private final boolean configuratorToClose;
	
	private SimpleHttpServer(HttpServerConfigurator configurator, boolean configuratorToClose) {
		this.configurator = configurator;
		this.configuratorToClose = configuratorToClose;
	}
	
	public SimpleHttpServer(HttpServerConfigurator configurator) {
		this(configurator, false);
	}
	public SimpleHttpServer() throws IOException {
		this(new HttpServerConfigurator(), true);
	}
	
	public SimpleHttpServer(int port) throws IOException {
		this(new HttpServerConfigurator().withPort(port), true);
	}
	
	@Override
	public void close() {
		if (configuratorToClose) {
			configurator.close();
		}
	}
	
	public void start(final SimpleHttpServerHandler handler) {
		new HttpServer(configurator, new HttpServerHandlerFactory() {
			@Override
			public HttpServerHandler create() {
				return new HttpServerHandlerToSimpleHttpServerHandler(handler);
			}
			
			@Override
			public void closed() {
				LOGGER.debug("Server closed");
			}

			@Override
			public void failed(IOException e) {
				LOGGER.error("Server could not be launched", e);
			}
		});
	}
}
