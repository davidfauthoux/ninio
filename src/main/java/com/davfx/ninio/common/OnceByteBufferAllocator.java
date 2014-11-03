package com.davfx.ninio.common;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;


public final class OnceByteBufferAllocator implements ByteBufferAllocator {
	private static final Logger LOGGER = LoggerFactory.getLogger(OnceByteBufferAllocator.class);
	private static final Config CONFIG = ConfigUtils.load(OnceByteBufferAllocator.class);

	private final ByteBuffer buffer;
	
	private static final AtomicInteger COUNT_ALLOC = new AtomicInteger();
	
	public OnceByteBufferAllocator() {
		this(CONFIG.getBytes("ninio.buffer.size.default").intValue());
	}
	public OnceByteBufferAllocator(int bufferSize) {
		buffer = ByteBuffer.allocate(bufferSize);
		LOGGER.debug(this + " ----> " + COUNT_ALLOC.incrementAndGet(), new Exception());
	}

	@Override
	public ByteBuffer allocate() {
		buffer.rewind();
		buffer.limit(buffer.capacity());
		return buffer;
	}
	
	@Override
	protected void finalize() {
		LOGGER.debug(this + " ----<<< " + COUNT_ALLOC.decrementAndGet());
	}
}
