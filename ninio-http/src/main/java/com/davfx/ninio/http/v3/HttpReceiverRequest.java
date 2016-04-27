package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.http.HttpRequest;

public interface HttpReceiverRequest {
	interface Send {
		Send post(ByteBuffer buffer);
		void finish();
		void cancel();
	}
	Send create(HttpRequest request);
}
