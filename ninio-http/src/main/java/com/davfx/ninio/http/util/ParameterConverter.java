package com.davfx.ninio.http.util;

public interface ParameterConverter<T> {
	T of(String s) throws Exception;
}
