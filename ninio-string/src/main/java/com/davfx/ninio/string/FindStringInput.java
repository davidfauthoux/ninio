package com.davfx.ninio.string;

final class FindStringInput<T> implements StringInput<T> {
	private final StringInput<T> possibleChars;
	private final StringInput<T> length;
	private final StringInput<T> wrappee;

	public FindStringInput(StringInput<T> possibleChars, StringInput<T> length, StringInput<T> wrappee) {
		this.possibleChars = possibleChars;
		this.length = length;
		this.wrappee = wrappee;
	}
	
	@Override
	public String get(T h) {
		String s = wrappee.get(h);
		if (s == null) {
			return null;
		}
		String chars = possibleChars.get(h);
		if (chars == null) {
			return null;
		}
		String l = length.get(h);
		if (l == null) {
			return null;
		}
		int lengthRequired = Integer.parseInt(l);
		int i = 0;
		StringBuilder found = null;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (chars.indexOf(c) >= 0) {
				if (found == null) {
					found = new StringBuilder();
				}
				found.append(c);
			} else {
				if ((found != null) && (found.length() == lengthRequired)) {
					return found.toString();
				}
				found = null;
			}
			i++;
		}
		if ((found != null) && (found.length() == lengthRequired)) {
			return found.toString();
		}
		return null;
	}
}