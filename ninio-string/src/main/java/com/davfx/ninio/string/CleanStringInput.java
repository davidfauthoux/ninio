package com.davfx.ninio.string;

final class CleanStringInput<T> implements StringInput<T> {
	private final StringInput<T> wrappee;

	public CleanStringInput(StringInput<T> wrappee) {
		this.wrappee = wrappee;
	}
	@Override
	public String get(T h) {
		String s = wrappee.get(h);
		if (s == null) {
			return null;
		}
		return clean(s);
	}
	
	public static String clean(String s) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (((c >= 'a') && (c <= 'z'))
			 || ((c >= 'A') && (c <= 'Z'))
			 || ((c >= '0') && (c <= '9'))) {
				b.append(c);
			}
		}
		return b.toString();
	}
}