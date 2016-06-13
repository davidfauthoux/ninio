package com.davfx.ninio.http.v3.service;

import com.davfx.ninio.http.v3.HttpMethod;
import com.davfx.ninio.http.v3.HttpRequest;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public final class HttpServiceRequest {
	
	public final HttpMethod method;
	public final ImmutableMultimap<String, String> headers;
	public final ImmutableList<String> path;
	public final ImmutableMultimap<String, Optional<String>> parameters;
	
	public HttpServiceRequest(HttpRequest request) {
		method = request.method;
		headers = request.headers;
		path = HttpRequest.path(request.path);
		parameters = HttpRequest.parameters(request.path);
	}
}
