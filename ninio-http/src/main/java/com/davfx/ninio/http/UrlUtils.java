package com.davfx.ninio.http;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Map;

import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;

public final class UrlUtils {

	private static final String UTF8 = "UTF-8";
	private static final char SPACE_ENCODED_PLUS = '+';
	private static final String SPACE_ENCODED_NUMBER = "%20";
	
	private UrlUtils() {
	}
	
	public static String encode(String component) {
		if (component == null) {
			return null;
		}
		try {
			return URLEncoder.encode(component, UTF8).replaceAll("\\" + SPACE_ENCODED_PLUS, SPACE_ENCODED_NUMBER);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static String decode(String component) {
		if (component == null) {
			return null;
		}
		try {
			return URLDecoder.decode(component, UTF8);
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static final class ParsedUrl {
		public final boolean secure;
		public final String host;
		public final int port;
		public final String path;
		public final ImmutableMultimap<String, String> headers;
		public ParsedUrl(boolean secure, String host, int port, String path, ImmutableMultimap<String, String> headers) {
			this.secure = secure;
			this.host = host;
			this.port = port;
			this.path = path;
			this.headers = headers;
		}
		
	}
	
	public static ParsedUrl parse(String url) {
		String protocol;
		final boolean secure;
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
		String a;
		final String path;
		if (i < 0) {
			a = url.substring(protocol.length());
			path = String.valueOf(HttpSpecification.PATH_SEPARATOR);
		} else {
			a = url.substring(protocol.length(), i);
			path = url.substring(i);
		}
		
		int k = a.indexOf(HttpSpecification.PORT_SEPARATOR);
		String host;
		final String hostInHeaders;
		final int port;
		if (k < 0) {
			host = a;
			port = defaultPort;
			hostInHeaders = host;
		} else {
			host = a.substring(0, k);
			port = Integer.parseInt(a.substring(k + 1));
			if (port == defaultPort) {
				hostInHeaders = host;
			} else {
				hostInHeaders = host + HttpSpecification.PORT_SEPARATOR + port;
			}
		}
		
		return new ParsedUrl(secure, host, port, path, ImmutableListMultimap.of(HttpHeaderKey.HOST, hostInHeaders));
	}
	
	public static ImmutableMultimap<String, String> merge(ImmutableMultimap<String, String> headers, ImmutableMultimap<String, String> moreHeaders) {
		ImmutableMultimap.Builder<String, String> m = ImmutableListMultimap.builder();
		m.putAll(headers);
		for (Map.Entry<String, Collection<String>> e : moreHeaders.asMap().entrySet()) {
			m.putAll(e.getKey(), e.getValue());
		}
		return m.build();
	}
}
