package com.davfx.ninio.http.util;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.http.HttpServer;
import com.davfx.ninio.http.HttpServerConfigurator;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;

public final class JsonHttpServer {
	private static final Logger LOGGER = LoggerFactory.getLogger(JsonHttpServer.class);

	private final HttpServerConfigurator configurator;
	
	public JsonHttpServer(HttpServerConfigurator configurator) {
		this.configurator = configurator;
	}
	
	public void start(final JsonHttpServerHandler handler) {
		new HttpServer(configurator, new HttpServerHandlerFactory() {
			@Override
			public HttpServerHandler create() {
				return new HttpServerHandlerToJsonHttpServerHandler(handler);
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
