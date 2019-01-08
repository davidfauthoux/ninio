package com.davfx.ninio.http.service;

import com.davfx.ninio.http.HttpMethod;
import com.davfx.ninio.http.HttpRequest;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultimap;

public final class HttpServiceRequest {
	
	public final HttpMethod method;
	public final ImmutableMultimap<String, String> headers;
	public final ImmutableList<String> path;
	public final ImmutableMultimap<String, Optional<String>> parameters;
	
	public HttpServiceRequest(HttpMethod method, ImmutableMultimap<String, String> headers, ImmutableList<String> path, ImmutableMultimap<String, Optional<String>> parameters) {
		this.method = method;
		this.headers = headers;
		this.path = path;
		this.parameters = parameters;
	}

	public HttpServiceRequest(HttpRequest request) {
		method = request.method;
		headers = request.headers;
		path = HttpRequest.path(request.path);
		parameters = HttpRequest.parameters(request.path);
	}
	
	@Override
	public String toString() {
		return "[method=" + method + ", path=" + path + ", headers=" + headers + ", parameters=" + parameters + "]";
	}
}
