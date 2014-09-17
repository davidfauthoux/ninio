package com.davfx.ninio.common;

import java.nio.ByteBuffer;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;


public final class AlwaysNewByteBufferAllocator implements ByteBufferAllocator {
	private static final Config CONFIG = ConfigUtils.load(AlwaysNewByteBufferAllocator.class);

	private final int bufferSize;
	
	public AlwaysNewByteBufferAllocator() {
		this(CONFIG.getBytes("ninio.buffer.size.default").intValue());
	}
	public AlwaysNewByteBufferAllocator(int bufferSize) {
		this.bufferSize = bufferSize;
	}

	@Override
	public ByteBuffer allocate() {
		return ByteBuffer.allocate(bufferSize);
	}
}
