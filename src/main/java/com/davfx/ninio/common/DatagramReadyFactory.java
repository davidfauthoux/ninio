package com.davfx.ninio.common;

public final class DatagramReadyFactory implements ReadyFactory {
	public DatagramReadyFactory() {
	}
	@Override
	public Ready create(Queue queue, ByteBufferAllocator allocator) {
		return new QueueReady(queue, new DatagramReady(queue.getSelector(), allocator));
	}
}
