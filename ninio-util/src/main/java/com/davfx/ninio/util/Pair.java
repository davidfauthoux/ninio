package com.davfx.ninio.util;

public final class Pair<T, U> {
	public final T first;
	public final U second;

	public Pair(T first, U second) {
		this.first = first;
		this.second = second;
	}

	@Override
	public String toString() {
		return "{ " + first + ", " + second + " }";
	}
}
