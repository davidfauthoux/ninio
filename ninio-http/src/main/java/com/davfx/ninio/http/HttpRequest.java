package com.davfx.ninio.http;

import java.nio.ByteBuffer;
import java.util.Map;

import com.davfx.ninio.core.Address;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMultimap;

public final class HttpRequest {
	
	public final Address address;
	public final boolean secure;
	public final HttpMethod method;
	public final HttpPath path;
	public final ImmutableMultimap<String, String> headers;
	
	public HttpRequest(Address address, boolean secure, HttpMethod method, HttpPath path, ImmutableMultimap<String, String> headers) {
		this.address = address;
		this.secure = secure;
		this.method = method;
		this.path = path;
		this.headers = headers;
	}
	public HttpRequest(Address address, boolean secure, HttpMethod method, HttpPath path) {
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
			return new HttpRequest(Address.of(url.substring(protocol.length()), defaultPort), secure, method, HttpPath.ROOT, headers);
		}
		return new HttpRequest(Address.of(url.substring(protocol.length(), i), defaultPort), secure, method, HttpPath.of(url.substring(i)), headers);
	}
	
	private static final String DEFAULT_USER_AGENT = "ninio"; // Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.111 Safari/537.36";
	private static final String DEFAULT_ACCEPT = "*/*";

	private static void appendHeader(StringBuilder buffer, String key, String value) {
		buffer.append(key).append(HttpSpecification.HEADER_KEY_VALUE_SEPARATOR).append(HttpSpecification.HEADER_BEFORE_VALUE).append(value.toString()).append(HttpSpecification.CR).append(HttpSpecification.LF);
	}
	
	public ByteBuffer toByteBuffer() {
		StringBuilder header = new StringBuilder();
		header.append(method.toString()).append(HttpSpecification.START_LINE_SEPARATOR).append(path).append(HttpSpecification.START_LINE_SEPARATOR).append(HttpSpecification.HTTP11).append(HttpSpecification.CR).append(HttpSpecification.LF);
		
		for (Map.Entry<String, String> h : headers.entries()) {
			appendHeader(header, h.getKey(), h.getValue());
		}
		if (!headers.containsKey(HttpHeaderKey.HOST)) {
			appendHeader(header, HttpHeaderKey.HOST, address.getHost()); //TODO check that! // Adding the port looks to fail with Apache/Coyote // + Http.PORT_SEPARATOR + request.getAddress().getPort());
		}
		if (!headers.containsKey(HttpHeaderKey.ACCEPT_ENCODING)) {
			appendHeader(header, HttpHeaderKey.ACCEPT_ENCODING, HttpHeaderValue.GZIP);
		}
		if (!headers.containsKey(HttpHeaderKey.CONNECTION)) {
			appendHeader(header, HttpHeaderKey.CONNECTION, HttpHeaderValue.KEEP_ALIVE);
		}
		if (!headers.containsKey(HttpHeaderKey.USER_AGENT)) {
			appendHeader(header, HttpHeaderKey.USER_AGENT, DEFAULT_USER_AGENT);
		}
		if (!headers.containsKey(HttpHeaderKey.ACCEPT)) {
			appendHeader(header, HttpHeaderKey.ACCEPT, DEFAULT_ACCEPT);
		}
		
		header.append(HttpSpecification.CR).append(HttpSpecification.LF);
		return ByteBuffer.wrap(header.toString().getBytes(Charsets.US_ASCII));
	}

}
