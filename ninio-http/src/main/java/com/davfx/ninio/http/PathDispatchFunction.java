package com.davfx.ninio.http;

import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Function;

public final class PathDispatchFunction implements Function<HttpRequest, HttpServerHandler> {
	private final Map<String, HttpServerHandler> map = new HashMap<>();
	private HttpServerHandler defaultHandler = null;
	
	public PathDispatchFunction() {
	}
	
	public PathDispatchFunction withDefault(HttpServerHandler handler) {
		defaultHandler = handler;
		return this;
	}
	public PathDispatchFunction add(String path, HttpServerHandler handler) {
		map.put(path, handler);
		return this;
	}
	
	@Override
	public HttpServerHandler apply(HttpRequest input) {
		HttpServerHandler h = map.get(HttpSpecification.PATH_SEPARATOR + input.path.path);
		if (h == null) {
			return defaultHandler;
		}
		return h;
	}
}
