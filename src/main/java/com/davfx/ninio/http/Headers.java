package com.davfx.ninio.http;

import java.util.HashMap;
import java.util.Map;

public final class Headers {
	private final Headers wrappee;
	private final Map<String, String> map = new HashMap<String, String>();
	
	public Headers() {
		this(null);
	}
	
	public Headers(Headers wrappee) {
		this.wrappee = wrappee;
	}
	
	public String get(String key) {
		String v = map.get(key);
		if (v == null) {
			return wrappee.get(key);
		}
		return v;
	}
	public Headers set(String key, String value) {
		map.put(key, value);
		return this;
	}
	public Iterable<String> keys() {
		return new UnionIterable<String>(map.keySet(), wrappee.keys());
	}
}
