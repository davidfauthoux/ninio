package com.davfx.ninio.string;

final class ReplaceStringInput<T> implements StringInput<T> {
	private final StringInput<T> toReplace;
	private final StringInput<T> replaceWith;
	private final StringInput<T> wrappee;

	public ReplaceStringInput(StringInput<T> toReplace, StringInput<T> replaceWith, StringInput<T> wrappee) {
		this.toReplace = toReplace;
		this.replaceWith = replaceWith;
		this.wrappee = wrappee;
	}
	@Override
	public String get(T h) {
		String s = wrappee.get(h);
		if (s == null) {
			return null;
		}
		String t = toReplace.get(h);
		if (t == null) {
			return null;
		}
		String w = replaceWith.get(h);
		if (w == null) {
			return null;
		}
		return s.replace(t, w);
	}
}
