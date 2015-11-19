package com.davfx.ninio.http;

import com.google.common.collect.ImmutableMultimap;

public final class HttpResponse {
	
	public final int status;
	public final String reason;
	public final ImmutableMultimap<String, HttpHeaderValue> headers;
	
	public HttpResponse(int status, String reason, ImmutableMultimap<String, HttpHeaderValue> headers) {
		this.status = status;
		this.reason = reason;
		this.headers = headers;
	}
	public HttpResponse(int status, String reason) {
		this(status, reason, ImmutableMultimap.<String, HttpHeaderValue>of());
	}
	public HttpResponse() {
		this(HttpStatus.OK, HttpMessage.OK);
	}
	
	@Override
	public String toString() {
		return "[status=" + status + ", reason=" + reason + ", headers=" + headers + "]";
	}
}