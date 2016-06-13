package com.davfx.ninio.http.v3.service;

public interface ParameterConverter<T> {
	T of(String s) throws Exception;
}
