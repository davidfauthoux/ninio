package com.davfx.ninio.http.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.davfx.ninio.http.Http;
import com.google.common.base.Splitter;


public final class HttpQuery {
	private final String path;
	private final Map<String, String> parameters = new LinkedHashMap<String, String>();

	public HttpQuery(String httpPath) {
		int i = httpPath.indexOf('?');
		String p;
		if (i < 0) {
			p = httpPath;
		} else {
			p = httpPath.substring(0, i);
			for (String kv : Splitter.on('&').split(httpPath.substring(i + 1))) {
				if (kv.isEmpty()) {
					continue;
				}
				List<String> j = Splitter.on('=').splitToList(kv);
				if (j.size() == 2) {
					parameters.put(j.get(0), Http.Url.decode(j.get(1)));
				} else {
					parameters.put(kv, null);
				}
			}
		}

		if (p.isEmpty()) {
			path = String.valueOf(Http.PATH_SEPARATOR);
		} else {
			path = Http.Url.decode(p);
		}
	}
	
	public String getPath() {
		return path;
	}
	
	public Parameters getParameters() {
		return new Parameters() {
			@Override
			public Iterable<String> keys() {
				return parameters.keySet();
			}
			
			@Override
			public String getValue(String key) {
				return parameters.get(key);
			}
			
			@Override
			public String toString() {
				return parameters.toString();
			}
		};
	}
}
