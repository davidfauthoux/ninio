package com.davfx.ninio.core;

import java.io.IOException;

public interface Future<T> {
	T get() throws IOException;
}
