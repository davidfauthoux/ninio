package com.davfx.ninio.string;

final class NullStringInput<T> implements StringInput<T> {
	public NullStringInput() {
	}
	@Override
	public String get(T h) {
		return null;
	}
}