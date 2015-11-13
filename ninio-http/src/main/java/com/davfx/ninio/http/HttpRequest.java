package com.davfx.ninio.http;

import com.davfx.ninio.core.Address;
import com.google.common.collect.ImmutableMultimap;

public final class HttpRequest {
	
	public final Address address;
	public final boolean secure;
	public final HttpMethod method;
	public final String path;
	public final ImmutableMultimap<String, String> headers;
	
	public HttpRequest(Address address, boolean secure, HttpMethod method, String path, ImmutableMultimap<String, String> headers) {
		this.address = address;
		this.secure = secure;
		this.method = method;
		this.path = path;
		this.headers = headers;
	}
	public HttpRequest(Address address, boolean secure, HttpMethod method, String path) {
		this(address, secure, method, path, ImmutableMultimap.<String, String>of());
	}
	
	@Override
	public String toString() {
		return "[address=" + address + ", secure=" + secure + ", method=" + method + ", path=" + path + ", headers=" + headers + "]";
	}
	
}
