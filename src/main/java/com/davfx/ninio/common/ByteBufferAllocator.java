package com.davfx.ninio.common;

import java.nio.ByteBuffer;

public interface ByteBufferAllocator {
	ByteBuffer allocate();
}
