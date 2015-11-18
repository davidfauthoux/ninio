package com.davfx.ninio.http;


public interface HttpRequestFilter {
	boolean accept(HttpRequest request);
}
