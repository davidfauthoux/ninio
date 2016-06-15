package com.davfx.ninio.string;

final class IfEqStringInput<T> implements StringInput<T> {
	private final StringInput<T> first;
	private final StringInput<T> second;
	private final StringInput<T> wrappeeIfEq;
	private final StringInput<T> wrappeeIfNeq;

	public IfEqStringInput(StringInput<T> first, StringInput<T> second, StringInput<T> wrappeeIfEq, StringInput<T> wrappeeIfNeq) {
		super();
		this.first = first;
		this.second = second;
		this.wrappeeIfEq = wrappeeIfEq;
		this.wrappeeIfNeq = wrappeeIfNeq;
	}

	@Override
	public String get(T h) {
		String f = first.get(h);
		if (f == null) {
			return null;
		}
		String s = second.get(h);
		if (s == null) {
			return null;
		}
		if (f.equals(s)) {
			return wrappeeIfEq.get(h);
		} else {
			return wrappeeIfNeq.get(h);
		}
	}
}
