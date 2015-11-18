package com.davfx.ninio.http;

import java.util.LinkedList;
import java.util.List;

import com.google.common.base.Function;

public final class HttpRequestFunctionContainer implements Function<HttpRequest, HttpServerHandler> {
	
	private final List<Function<HttpRequest, HttpServerHandler>> map = new LinkedList<>();
	
	public HttpRequestFunctionContainer() {
	}
	
	public HttpRequestFunctionContainer add(final HttpRequestFilter filter, final HttpServerHandler handler) {
		map.add(new Function<HttpRequest, HttpServerHandler>() {
			@Override
			public HttpServerHandler apply(HttpRequest request) {
				if (filter.accept(request)) {
					return handler;
				}
				return null;
			}
		});
		return this;
	}
	
	@Override
	public HttpServerHandler apply(HttpRequest request) {
		for (Function<HttpRequest, HttpServerHandler> f : map) {
			HttpServerHandler h = f.apply(request);
			if (h != null) {
				return h;
			}
		}
		return null;
	}

}
