package com.davfx.ninio.http.util;

import java.util.HashMap;
import java.util.Map;

public final class PathDispatchSimpleHttpServerHandler implements SimpleHttpServerHandler {
	private final Map<String, SimpleHttpServerHandler> handlers = new HashMap<String, SimpleHttpServerHandler>();

	public PathDispatchSimpleHttpServerHandler() {
	}
	
	public PathDispatchSimpleHttpServerHandler add(String path, SimpleHttpServerHandler handler) {
		handlers.put(path, handler);
		return this;
	}
	
	@Override
	public String get(String path, Parameters parameters) {
		SimpleHttpServerHandler h = handlers.get(path);
		if (h == null) {
			return null;
		}
		return h.get(path, parameters);
	}
	
	@Override
	public String put(String path, Parameters parameters) {
		SimpleHttpServerHandler h = handlers.get(path);
		if (h == null) {
			return null;
		}
		return h.put(path, parameters);
	}
	
	@Override
	public String delete(String path, Parameters parameters) {
		SimpleHttpServerHandler h = handlers.get(path);
		if (h == null) {
			return null;
		}
		return h.delete(path, parameters);
	}
	
	@Override
	public String head(String path, Parameters parameters) {
		SimpleHttpServerHandler h = handlers.get(path);
		if (h == null) {
			return null;
		}
		return h.head(path, parameters);
	}
	
	@Override
	public String post(String path, Parameters parameters, InMemoryPost post) {
		SimpleHttpServerHandler h = handlers.get(path);
		if (h == null) {
			return null;
		}
		return h.post(path, parameters, post);
	}

}
