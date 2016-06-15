package com.davfx.ninio.http.v3.service.controllers;

import com.davfx.ninio.http.v3.HttpMethod;
import com.davfx.ninio.http.v3.service.HttpController;
import com.davfx.ninio.http.v3.service.HttpServiceRequest;
import com.davfx.ninio.http.v3.service.ResourcesHandler;
import com.davfx.ninio.http.v3.service.annotations.Route;

public final class Assets implements HttpController {
	
	private final ResourcesHandler handler;
	
	public Assets(String dir, String index) {
		handler = new ResourcesHandler(this.getClass(), dir, index);
	}
	
	@Route(method = HttpMethod.GET)
	public Http serve(HttpServiceRequest request) throws Exception {
		return handler.handle(request.path);
	}
}
