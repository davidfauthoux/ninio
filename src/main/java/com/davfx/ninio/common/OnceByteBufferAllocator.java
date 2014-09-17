package com.davfx.ninio.common;

import java.nio.ByteBuffer;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;


public final class OnceByteBufferAllocator implements ByteBufferAllocator {
	private static final Config CONFIG = ConfigUtils.load(OnceByteBufferAllocator.class);

	private final ByteBuffer buffer;
	
	public OnceByteBufferAllocator() {
		this(CONFIG.getBytes("ninio.buffer.size.default").intValue());
	}
	public OnceByteBufferAllocator(int bufferSize) {
		buffer = ByteBuffer.allocate(bufferSize);
	}

	@Override
	public ByteBuffer allocate() {
		buffer.rewind();
		buffer.limit(buffer.capacity());
		return buffer;
	}
}
