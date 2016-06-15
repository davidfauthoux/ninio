package com.davfx.ninio.util;

public final class Mutable<T> {
	public T value = null;
	
	public Mutable() {
	}
	
	public Mutable(T value) {
		this.value = value;
	}
}
