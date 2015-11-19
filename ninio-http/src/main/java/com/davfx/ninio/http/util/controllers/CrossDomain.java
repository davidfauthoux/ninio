package com.davfx.ninio.http.util.controllers;

import com.davfx.ninio.http.HttpHeaderValue;
import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.util.HttpController;
import com.davfx.ninio.http.util.annotations.Route;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public final class CrossDomain implements HttpController {
	public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
	public static final HttpHeaderValue WILDCARD = HttpHeaderValue.simple("*");
	
	public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
	public static final HttpHeaderValue ACCESS_CONTROL_ALLOWED_METHODS = new HttpHeaderValue(ImmutableList.of("GET", "PUT", "POST", "DELETE", "HEAD"), ImmutableMultimap.<String, Optional<String>>of());
	
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
