package com.davfx.ninio.http.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpServerHandler;

public final class PathDispatchHttpServerHandler implements HttpServerHandler {
	private final Map<String, HttpServerHandler> handlers = new HashMap<String, HttpServerHandler>();
	private HttpServerHandler defaultHandler = null;
	private HttpServerHandler currentHandler = null;

	public PathDispatchHttpServerHandler() {
	}
	
	public PathDispatchHttpServerHandler fallback(HttpServerHandler handler) {
		defaultHandler = handler;
		return this;
	}
	
	public PathDispatchHttpServerHandler add(String path, HttpServerHandler handler) {
		handlers.put(path, handler);
		return this;
	}
	
	@Override
	public void handle(HttpRequest request) {
		currentHandler = handlers.get(new HttpQuery(request.getPath()).getPath());
		if (currentHandler == null) {
			currentHandler = defaultHandler;
		}
		if (currentHandler == null) {
			return;
		}
		currentHandler.handle(request);
	}
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		if (currentHandler == null) {
			return;
		}
		currentHandler.handle(address, buffer);
	}
	
	@Override
	public void close() {
		if (currentHandler == null) {
			return;
		}
		currentHandler.close();
		currentHandler = null;
	}
	
	@Override
	public void failed(IOException e) {
		if (currentHandler == null) {
			return;
		}
		currentHandler.failed(e);
		currentHandler = null;
	}
	
	@Override
	public void ready(Write write) {
		if (currentHandler == null) {
			write.failed(null);
			return;
		}
		currentHandler.ready(write);
	}
}
