package com.davfx.ninio.string;

public interface StringInputFactory<T> {
	StringInput<T> build(StringInput<T>[] inputs);
}