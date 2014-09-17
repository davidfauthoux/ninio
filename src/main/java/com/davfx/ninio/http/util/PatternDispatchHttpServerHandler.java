package com.davfx.ninio.http.util;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.http.HttpRequest;
import com.davfx.ninio.http.HttpServerHandler;

public final class PatternDispatchHttpServerHandler implements HttpServerHandler {
	private final Map<Pattern, HttpServerHandler> handlers = new LinkedHashMap<Pattern, HttpServerHandler>();
	private HttpServerHandler currentHandler = null;

	public PatternDispatchHttpServerHandler() {
	}
	
	public PatternDispatchHttpServerHandler add(Pattern pattern, HttpServerHandler handler) {
		handlers.put(pattern, handler);
		return this;
	}
	
	@Override
	public void handle(HttpRequest request) {
		String path = new HttpQuery(request.getPath()).getPath();
		for (Map.Entry<Pattern, HttpServerHandler> e : handlers.entrySet()) {
			if (e.getKey().matcher(path).matches()) {
				currentHandler = e.getValue();
				break;
			}
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
