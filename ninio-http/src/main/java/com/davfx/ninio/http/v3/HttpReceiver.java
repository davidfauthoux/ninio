package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.http.HttpResponse;

public interface HttpReceiver {
	interface ContentReceiver {
		void received(HttpClient client, ByteBuffer buffer);
		void ended(HttpClient client);
	}
	ContentReceiver received(HttpClient client, HttpResponse response);
}
