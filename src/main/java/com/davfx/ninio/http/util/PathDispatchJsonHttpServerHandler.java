package com.davfx.ninio.http.util;

import java.util.HashMap;
import java.util.Map;

public final class PathDispatchJsonHttpServerHandler implements JsonHttpServerHandler {
	private final Map<String, JsonHttpServerHandler> handlers = new HashMap<String, JsonHttpServerHandler>();

	public PathDispatchJsonHttpServerHandler() {
	}
	
	public PathDispatchJsonHttpServerHandler add(String path, JsonHttpServerHandler handler) {
		handlers.put(path, handler);
		return this;
	}

	@Override
	public void get(String path, Parameters parameters, Callback callback) {
		JsonHttpServerHandler h = handlers.get(path);
		if (h == null) {
			callback.send(null);
			return;
		}
		h.get(path, parameters, callback);
	}

	@Override
	public void put(String path, Parameters parameters, Callback callback) {
		JsonHttpServerHandler h = handlers.get(path);
		if (h == null) {
			callback.send(null);
			return;
		}
		h.put(path, parameters, callback);
	}
	
	@Override
	public void delete(String path, Parameters parameters, Callback callback) {
		JsonHttpServerHandler h = handlers.get(path);
		if (h == null) {
			callback.send(null);
			return;
		}
		h.delete(path, parameters, callback);
	}
	
	@Override
	public void head(String path, Parameters parameters, Callback callback) {
		JsonHttpServerHandler h = handlers.get(path);
		if (h == null) {
			callback.send(null);
			return;
		}
		h.head(path, parameters, callback);
	}

	@Override
	public void post(String path, Parameters parameters, InMemoryPost post, Callback callback) {
		JsonHttpServerHandler h = handlers.get(path);
		if (h == null) {
			callback.send(null);
			return;
		}
		h.post(path, parameters, post, callback);
	}
	
}
