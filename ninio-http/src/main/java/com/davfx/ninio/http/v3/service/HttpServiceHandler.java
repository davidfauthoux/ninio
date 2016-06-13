package com.davfx.ninio.http.v3.service;

public interface HttpServiceHandler {
	HttpController.Http handle(HttpServiceRequest request, HttpPost post) throws Exception;
}
