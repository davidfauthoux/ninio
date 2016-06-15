package com.davfx.ninio.string;

final class BeforeStringInput<T> implements StringInput<T> {
	private final StringInput<T> separator;
	private final StringInput<T> wrappee;

	public BeforeStringInput(StringInput<T> separator, StringInput<T> wrappee) {
		this.separator = separator;
		this.wrappee = wrappee;
	}
	@Override
	public String get(T h) {
		String s = wrappee.get(h);
		if (s == null) {
			return null;
		}
		String sep = separator.get(h);
		if (sep == null) {
			return null;
		}
		int k = s.indexOf(sep);
		if (k < 0) {
			return null;
		}
		return s.substring(0, k);
	}
}