package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

public interface HttpContentReceiver {
	void received(ByteBuffer buffer);
	void ended();
}
