package com.davfx.ninio.core.v3;

import java.nio.ByteBuffer;

public interface ByteBufferAllocator {
	ByteBuffer allocate();
}
