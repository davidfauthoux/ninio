package com.davfx.ninio.http.util;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.davfx.ninio.http.Http;


public final class HttpQuery {
	private final String path;
	private final Map<String, String> parameters = new LinkedHashMap<String, String>();

	public HttpQuery(String httpPath) {
		int i = httpPath.indexOf('?');
		if (i < 0) {
			path = httpPath;
		} else {
			path = httpPath.substring(0, i);
			if (!path.isEmpty()) {
				for (String kv : split(httpPath.substring(i + 1), '&')) {
					if (kv.isEmpty()) {
						continue;
					}
					Iterator<String> j = split(kv, '=').iterator();
					parameters.put(j.next(), Http.Url.decode(j.next()));
				}
			}
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
	
	private static Iterable<String> split(String s, char c) {
		List<String> l = new LinkedList<String>();
		int j = 0;
		while (true) {
			int i = s.indexOf(c, j);
			if (i < 0) {
				l.add(s.substring(j));
				break;
			}
			l.add(s.substring(j, i));
			j = i + 1;
		}
		return l;
	}
}
