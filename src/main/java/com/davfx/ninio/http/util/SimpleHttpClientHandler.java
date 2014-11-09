package com.davfx.ninio.http.util;

public interface SimpleHttpClientHandler {
	void handle(int status, String reason, Parameters headers, InMemoryPost body);
}