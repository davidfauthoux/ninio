package com.davfx.ninio.http.util;

import com.davfx.ninio.http.HttpRequest;

public interface HttpServiceHandler {
	HttpController.Http handle(HttpRequest request, HttpPost post) throws Exception;
}
