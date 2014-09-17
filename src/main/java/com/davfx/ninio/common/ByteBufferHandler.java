package com.davfx.ninio.common;

import java.nio.ByteBuffer;

public interface ByteBufferHandler {
	void handle(Address address, ByteBuffer buffer);
}
