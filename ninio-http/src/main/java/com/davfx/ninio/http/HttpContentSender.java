package com.davfx.ninio.http;

import java.nio.ByteBuffer;

public interface HttpContentSender {
	HttpContentSender send(ByteBuffer buffer);
	void finish();
	void cancel();
}
