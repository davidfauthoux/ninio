package com.davfx.ninio.http.util;

public interface SimpleHttpServerHandler {
	String get(String path, Parameters parameters);
	String head(String path, Parameters parameters);
	String delete(String path, Parameters parameters);
	String post(String path, Parameters parameters, InMemoryPost post);
	String put(String path, Parameters parameters);
}