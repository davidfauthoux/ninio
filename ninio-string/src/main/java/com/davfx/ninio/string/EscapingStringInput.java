package com.davfx.ninio.string;

final class EscapingStringInput<T> implements StringInput<T> {
	private final String raw;

	public EscapingStringInput(String raw) {
		StringBuilder b = new StringBuilder();
		boolean escaping = false;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (escaping) {
				escaping = false;
				b.append(c);
			} else if (c == '\\') {
				escaping = true;
			} else {
				b.append(c);
			}
		}
		this.raw = b.toString();
	}
	@Override
	public String get(T h) {
		return raw;
	}
}