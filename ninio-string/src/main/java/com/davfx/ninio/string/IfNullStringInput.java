package com.davfx.ninio.string;

import java.util.LinkedList;
import java.util.List;

final class IfNullStringInput<T> implements StringInput<T> {
	private final List<StringInput<T>> wrappees = new LinkedList<StringInput<T>>();
	public IfNullStringInput() {
	}
	void add(StringInput<T> i) {
		wrappees.add(i);
	}
	@Override
	public String get(T h) {
		for (StringInput<T> wrappee : wrappees) {
			String s = wrappee.get(h);
			if (s != null) {
				return s;
			}
		}
		return null;
	}
}