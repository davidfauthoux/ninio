package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import com.davfx.ninio.core.Address;
import com.davfx.util.Pair;

public final class DispatchHttpServerHandler implements HttpServerHandler {
	private final List<Pair<HttpRequestFilter, HttpServerHandler>> handlers = new LinkedList<>();
	private HttpServerHandler currentHandler = null;

	public DispatchHttpServerHandler() {
	}
	
	public DispatchHttpServerHandler add(HttpRequestFilter filter, HttpServerHandler handler) {
		handlers.add(new Pair<HttpRequestFilter, HttpServerHandler>(filter, handler));
		return this;
	}
	
	@Override
	public void handle(HttpRequest request) {
		for (Pair<HttpRequestFilter, HttpServerHandler> p : handlers) {
			if (p.first.accept(request)) {
				currentHandler = p.second;
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
