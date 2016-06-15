package com.davfx.ninio.string;

import java.util.LinkedList;
import java.util.List;

final class AppendStringInput<T> implements StringInput<T> {
	private final List<StringInput<T>> wrappees = new LinkedList<StringInput<T>>();
	public AppendStringInput() {
	}
	
	public AppendStringInput<T> add(StringInput<T> i) {
		wrappees.add(i);
		return this;
	}

	@Override
	public String get(T h) {
		StringBuilder b = new StringBuilder();
		for (StringInput<T> input : wrappees) {
			String v = input.get(h);
			if (v == null) {
				return null;
			}
			b.append(v);
		}
		return b.toString();
	}
}