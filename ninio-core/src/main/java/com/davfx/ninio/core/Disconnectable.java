package com.davfx.ninio.core;

public interface Disconnectable extends AutoCloseable {
	void close();
}
