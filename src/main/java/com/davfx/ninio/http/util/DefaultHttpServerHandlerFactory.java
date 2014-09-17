package com.davfx.ninio.http.util;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpServerHandler;
import com.davfx.ninio.http.HttpServerHandlerFactory;

public class DefaultHttpServerHandlerFactory implements HttpServerHandlerFactory {
	private static final Logger LOGGER = LoggerFactory.getLogger(DefaultHttpServerHandlerFactory.class);
	
	public DefaultHttpServerHandlerFactory() {
	}
	
	@Override
	public void closed() {
	}
	
	@Override
	public void failed(IOException e) {
		LOGGER.error("Failed", e);
	}
	
	@Override
	public HttpServerHandler create() {
		return new HttpServerHandler() {
			@Override
			public void ready(Write write) {
				write.failed(null);
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
}
