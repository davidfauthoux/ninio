package com.davfx.ninio.http.util;

import java.io.IOException;

import com.davfx.ninio.http.HttpRequest;

public interface HttpServiceHandler {
	void handle(HttpRequest request, HttpPost post, HttpServiceResult result) throws IOException;
}
