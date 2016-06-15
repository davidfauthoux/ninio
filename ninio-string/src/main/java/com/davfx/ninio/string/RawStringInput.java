package com.davfx.ninio.string;

final class RawStringInput<T> implements StringInput<T> {
	private final String raw;

	public RawStringInput(String raw) {
		this.raw = raw;
	}
	@Override
	public String get(T h) {
		return raw;
	}
}