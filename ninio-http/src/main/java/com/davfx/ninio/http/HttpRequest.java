package com.davfx.ninio.http;

import com.davfx.ninio.core.Address;
import com.google.common.collect.ImmutableMultimap;

public final class HttpRequest {
	
	public final Address address;
	public final boolean secure;
	public final HttpMethod method;
	public final HttpPath path;
	public final ImmutableMultimap<String, HttpHeaderValue> headers;
	
	public HttpRequest(Address address, boolean secure, HttpMethod method, HttpPath path, ImmutableMultimap<String, HttpHeaderValue> headers) {
		this.address = address;
		this.secure = secure;
		this.method = method;
		this.path = path;
		this.headers = headers;
	}
	public HttpRequest(Address address, boolean secure, HttpMethod method, HttpPath path) {
		this(address, secure, method, path, ImmutableMultimap.<String, HttpHeaderValue>of());
	}
	
	@Override
	public String toString() {
		return "[address=" + address + ", secure=" + secure + ", method=" + method + ", path=" + path + ", headers=" + headers + "]";
	}
	
	public static HttpRequest of(String url) {
		return of(url, HttpMethod.GET, ImmutableMultimap.<String, HttpHeaderValue>of());
	}
	public static HttpRequest of(String url, HttpMethod method, ImmutableMultimap<String, HttpHeaderValue> headers) {
		String protocol;
		boolean secure;
		int defaultPort;
		if (url.startsWith(HttpSpecification.PROTOCOL)) {
			protocol = HttpSpecification.PROTOCOL;
			secure = false;
			defaultPort = HttpSpecification.DEFAULT_PORT;
		} else if (url.startsWith(HttpSpecification.SECURE_PROTOCOL)) {
			protocol = HttpSpecification.SECURE_PROTOCOL;
			secure = true;
			defaultPort = HttpSpecification.DEFAULT_SECURE_PORT;
		} else {
			throw new IllegalArgumentException("URL must starts with " + HttpSpecification.PROTOCOL + " or " + HttpSpecification.SECURE_PROTOCOL);
		}

		int i = url.indexOf(HttpSpecification.PATH_SEPARATOR, protocol.length());
		if (i < 0) {
			return new HttpRequest(Address.of(url.substring(protocol.length()), defaultPort), secure, method, HttpPath.ROOT, headers);
		}
		return new HttpRequest(Address.of(url.substring(protocol.length(), i), defaultPort), secure, method, new HttpPath(url.substring(i)), headers);
	}
}
