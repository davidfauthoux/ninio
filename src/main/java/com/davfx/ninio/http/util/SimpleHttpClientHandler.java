package com.davfx.ninio.http.util;

public interface SimpleHttpClientHandler {
	void handle(int status, String reason, InMemoryPost body);
}