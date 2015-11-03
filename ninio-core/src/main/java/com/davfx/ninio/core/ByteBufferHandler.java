package com.davfx.ninio.core;

import java.nio.ByteBuffer;

public interface ByteBufferHandler {
	void handle(Address address, ByteBuffer buffer);
}
