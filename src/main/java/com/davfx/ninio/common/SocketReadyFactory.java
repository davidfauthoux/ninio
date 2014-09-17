package com.davfx.ninio.common;

public final class SocketReadyFactory implements ReadyFactory {
	public SocketReadyFactory() {
	}
	@Override
	public Ready create(Queue queue, ByteBufferAllocator allocator) {
		return new QueueReady(queue, new SocketReady(queue.getSelector(), allocator));
	}
}
