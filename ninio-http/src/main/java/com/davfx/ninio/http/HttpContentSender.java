package com.davfx.ninio.http;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Connecter;

public interface HttpContentSender {
	void send(ByteBuffer buffer, Connecter.Connecting.Callback callback);
	void finish();
	void cancel();
}
