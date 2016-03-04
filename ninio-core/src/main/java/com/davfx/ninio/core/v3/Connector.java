package com.davfx.ninio.core.v3;

public interface Connector extends Connected {
	void connect();
	void disconnect();
}
