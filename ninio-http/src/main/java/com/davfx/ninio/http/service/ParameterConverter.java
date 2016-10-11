package com.davfx.ninio.http.service;

public interface ParameterConverter<T> {
	T of(String s);
}
