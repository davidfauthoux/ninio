package com.davfx.ninio.http.util;

import java.util.LinkedHashMap;
import java.util.Map;

import com.davfx.ninio.http.Http;


public final class HttpQueryBuilder {
	private final String path;
	private final Map<String, String> parameters = new LinkedHashMap<String, String>();
	public HttpQueryBuilder(String path) {
		this.path = path;
	}
	
	public HttpQueryBuilder add(String key, String value) {
		parameters.put(key, value);
		return this;
	}

	@Override
	public String toString() {
		if (parameters.isEmpty()) {
			return path;
		}
		StringBuilder b = new StringBuilder(path);
		b.append('?');
		boolean first = true;
		for (Map.Entry<String, String> e : parameters.entrySet()) {
			if (first) {
				first = false;
			} else {
				b.append('&');
			}
			b.append(e.getKey()).append('=').append(Http.Url.encode(e.getValue()));
		}
		return b.toString();
	}
}
