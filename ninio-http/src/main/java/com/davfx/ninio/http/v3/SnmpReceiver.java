package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.http.HttpResponse;

public interface SnmpReceiver {
	void received(HttpResponse response);
	void received(ByteBuffer buffer);
	void ended();
}
