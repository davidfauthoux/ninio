package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

public interface HttpContentSender {
	HttpContentSender send(ByteBuffer buffer);
	void finish();
	void cancel();
}
