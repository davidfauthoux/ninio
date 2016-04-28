package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

public interface HttpReceiverRequest {
	interface Send {
		Send post(ByteBuffer buffer);
		void finish();
		void cancel();
	}
	Send create(HttpRequest request);
}
