package com.davfx.ninio.core.v3;

public interface Connectable extends Connected {
	void connect();
	void disconnect();
}
