package com.davfx.ninio.http.util;

public interface Parameters {
	Iterable<String> keys();
	String getValue(String key);
}
