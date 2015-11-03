package com.davfx.ninio.core;

import java.nio.ByteBuffer;

public interface ByteBufferAllocator {
	ByteBuffer allocate();
}
