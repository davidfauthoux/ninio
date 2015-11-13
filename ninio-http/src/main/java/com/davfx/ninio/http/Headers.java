package com.davfx.ninio.http;

import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

public final class Headers {
	
	public static interface GetValue extends Iterable<String> {
		String first();
	}
	public static interface SetValue {
		void add(String value);
	}
	
	private static final class Value implements GetValue, SetValue {
		private final Deque<String> values = new LinkedList<>();
		public Value() {
		}
		
		@Override
		public Iterator<String> iterator() {
			return values.iterator();
		}
		
		@Override
		public String first() {
			return values.getFirst();
		}
		
		@Override
		public void add(String value) {
			values.add(value);
		}
	}
	
	private final Map<String, Value> map = new HashMap<String, Value>();
	
	public Headers() {
	}
	
	public GetValue get(String key) {
		return map.get(key);
	}
	
	public SetValue set(String key) {
		Value v = map.get(key);
		if (v == null) {
			v = new Value();
			map.put(key, v);
		}
		return v;
	}
}
