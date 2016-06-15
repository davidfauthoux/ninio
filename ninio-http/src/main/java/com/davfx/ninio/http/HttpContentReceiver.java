package com.davfx.ninio.http;

import java.nio.ByteBuffer;

public interface HttpContentReceiver {
	void received(ByteBuffer buffer);
	void ended();
}
