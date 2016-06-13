package com.davfx.ninio.http.v3.service.controllers;

import com.davfx.ninio.http.v3.HttpMethod;
import com.davfx.ninio.http.v3.service.HttpController;
import com.davfx.ninio.http.v3.service.annotations.Route;

public final class CrossDomain implements HttpController {
	public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
	public static final String WILDCARD = "*";
	
	public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
	public static final String ACCESS_CONTROL_ALLOWED_METHODS = "GET, PUT, POST, DELETE, HEAD";
	
	public CrossDomain() {
	}
	
	private static HttpController.HttpWrap WRAP = new HttpWrap() {
		@Override
		public void handle(Http http) throws Exception {
			http.header(ACCESS_CONTROL_ALLOW_ORIGIN, WILDCARD);
			http.header(ACCESS_CONTROL_ALLOW_METHODS, ACCESS_CONTROL_ALLOWED_METHODS);
		}
	};
	
	@Route(method = HttpMethod.GET)
	public Http addHeadersToGet() {
		return Http.wrap(WRAP);
	}
	@Route(method = HttpMethod.PUT)
	public Http addHeadersToPut() {
		return Http.wrap(WRAP);
	}
	@Route(method = HttpMethod.POST)
	public Http addHeadersToPost() {
		return Http.wrap(WRAP);
	}
	@Route(method = HttpMethod.DELETE)
	public Http addHeadersToDelete() {
		return Http.wrap(WRAP);
	}
	@Route(method = HttpMethod.HEAD)
	public Http addHeadersToHead() {
		return Http.wrap(WRAP);
	}

}
