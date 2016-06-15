package com.davfx.ninio.http;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import com.davfx.ninio.core.Address;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
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
	
	public static ImmutableList<String> path(String path) {
		int i = path.indexOf(HttpSpecification.PARAMETERS_START);
		if (i < 0) {
			i = path.indexOf(HttpSpecification.HASH_SEPARATOR);
		} else {
			int j = path.indexOf(HttpSpecification.HASH_SEPARATOR);
			if ((j >= 0) && (j < i)) {
				i = j;
			}
		}
		
		if (i < 0) {
			i = path.length();
		}
		
		String p = path.substring(0, i);
		if (p.charAt(0) != HttpSpecification.PATH_SEPARATOR) {
			throw new IllegalArgumentException("Path must start with '" + HttpSpecification.PATH_SEPARATOR + "': " + p);
		}
		String s = p.substring(1);
		Deque<String> l = new LinkedList<>();
		for (String k : Splitter.on(HttpSpecification.PATH_SEPARATOR).splitToList(s)) {
			if (k.isEmpty()) {
				continue;
			}
			if (k.equals(".")) {
				continue;
			}
			if (k.equals("..")) {
				if (!l.isEmpty()) {
					l.removeLast();
				}
				continue;
			}
			l.add(k);
		}
		return ImmutableList.copyOf(l);
	}

	public static ImmutableMultimap<String, Optional<String>> parameters(String path) {
		int i = path.indexOf(HttpSpecification.PARAMETERS_START);
		if (i < 0) {
			return ImmutableMultimap.of();
		} else {
			int j = path.indexOf(HttpSpecification.HASH_SEPARATOR);
			String s;
			if (j < 0) {
				s = path.substring(i + 1);
			} else {
				s = path.substring(i + 1, j);
			}
			ImmutableMultimap.Builder<String, Optional<String>> m = ImmutableMultimap.builder();
			for (String kv : Splitter.on(HttpSpecification.PARAMETERS_SEPARATOR).splitToList(s)) {
				List<String> l = Splitter.on(HttpSpecification.PARAMETER_KEY_VALUE_SEPARATOR).splitToList(kv);
				if (!l.isEmpty()) {
					if (l.size() == 1) {
						m.put(UrlUtils.decode(l.get(0)), Optional.<String>absent());
					} else {
						m.put(UrlUtils.decode(l.get(0)), Optional.of(UrlUtils.decode(l.get(1))));
					}
				}
			}
			return m.build();
		}
	}
}
