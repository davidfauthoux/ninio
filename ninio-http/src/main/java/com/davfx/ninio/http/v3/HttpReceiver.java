package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.http.HttpResponse;

public interface HttpReceiver {
	void received(HttpClient client, HttpResponse response);
	void received(HttpClient client, ByteBuffer buffer);
	void ended(HttpClient client);
}
