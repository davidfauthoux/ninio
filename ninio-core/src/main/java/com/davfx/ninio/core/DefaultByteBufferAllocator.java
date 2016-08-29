package com.davfx.ninio.core;

import java.nio.ByteBuffer;

import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

public final class DefaultByteBufferAllocator implements ByteBufferAllocator {
	private static final Config CONFIG = ConfigUtils.load(new com.davfx.ninio.core.dependencies.Dependencies(), DefaultByteBufferAllocator.class);
	private static final int DEFAULT_SIZE = CONFIG.getBytes("buffer.default").intValue();
	
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
