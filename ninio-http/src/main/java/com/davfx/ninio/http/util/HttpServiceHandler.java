package com.davfx.ninio.http.util;

import java.io.IOException;
import java.io.InputStream;

import com.davfx.ninio.http.HttpRequest;

public interface HttpServiceHandler {
	void handle(HttpRequest request, InputStream post, HttpServiceResult result) throws IOException;
}
