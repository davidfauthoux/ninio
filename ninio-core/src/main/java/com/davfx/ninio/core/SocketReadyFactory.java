package com.davfx.ninio.core;

public final class SocketReadyFactory implements ReadyFactory {
	public SocketReadyFactory() {
	}
	@Override
	public Ready create(Queue queue) {
		return new QueueReady(queue, new SocketReady(queue.getSelector(), queue.allocator()));
	}
}
