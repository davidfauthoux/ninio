package com.davfx.ninio.http.service;

public interface HttpServiceHandler {
	HttpController.Http handle(HttpServiceRequest request, HttpPost post) throws Exception;
}
