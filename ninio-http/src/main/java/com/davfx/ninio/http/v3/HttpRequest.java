package com.davfx.ninio.http.v3;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.http.HttpSpecification;
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
	
	public static HttpRequest of(String url) {
		return of(url, HttpMethod.GET, ImmutableMultimap.<String, String>of());
	}
	public static HttpRequest of(String url, HttpMethod method, ImmutableMultimap<String, String> headers) {
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
			String a = url.substring(protocol.length());
			int k = a.indexOf(':');
			Address address;
			if (k < 0) {
				address = new Address(a, defaultPort);
			} else {
				String h = a.substring(0, k);
				int p = Integer.parseInt(a.substring(k + 1));
				address = new Address(h, p);
			}
			return new HttpRequest(address, secure, method, String.valueOf(HttpSpecification.PATH_SEPARATOR), headers);
		} else {
			String a = url.substring(protocol.length(), i);
			int k = a.indexOf(':');
			Address address;
			if (k < 0) {
				address = new Address(a, defaultPort);
			} else {
				String h = a.substring(0, k);
				int p = Integer.parseInt(a.substring(k + 1));
				address = new Address(h, p);
			}
			return new HttpRequest(address, secure, method, url.substring(i), headers);
		}
	}
	
}
