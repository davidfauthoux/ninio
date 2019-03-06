package com.davfx.ninio.string;

final class GetSuffixStringInput<T> implements StringInput<T> {
	private final StringInput<T> suffix;
	private final StringInput<T> wrappee;

	public GetSuffixStringInput(StringInput<T> suffix, StringInput<T> wrappee) {
		this.suffix = suffix;
		this.wrappee = wrappee;
	}
	@Override
	public String get(T h) {
		String s = wrappee.get(h);
		if (s == null) {
			return null;
		}
		String p = suffix.get(h);
		if (p == null) {
			return null;
		}
		if (s.endsWith(p)) {
			return p;
		} else {
			return null;
		}
	}
}
