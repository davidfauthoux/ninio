package com.davfx.ninio.http;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.SendCallback;

public interface HttpContentSender {
	HttpContentSender send(ByteBuffer buffer, SendCallback callback);
	void finish();
	void cancel();
}
