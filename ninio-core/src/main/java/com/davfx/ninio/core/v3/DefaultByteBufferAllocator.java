package com.davfx.ninio.core.v3;

import java.nio.ByteBuffer;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class DefaultByteBufferAllocator implements ByteBufferAllocator {
	private static final Config CONFIG = ConfigUtils.load(DefaultByteBufferAllocator.class);
	private static final int DEFAULT_SIZE = CONFIG.getBytes("ninio.buffer.default").intValue();
	
	private final int size;
	
	public DefaultByteBufferAllocator() {
		this(DEFAULT_SIZE);
	}
	public DefaultByteBufferAllocator(int size) {
		this.size = size;
	}
	
	@Override
	public ByteBuffer allocate() {
		return ByteBuffer.allocate(size);
	}
}
