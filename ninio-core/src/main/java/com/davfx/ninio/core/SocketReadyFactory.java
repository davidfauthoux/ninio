package com.davfx.ninio.core;

public final class SocketReadyFactory implements ReadyFactory {
	private final Queue queue;
	public SocketReadyFactory(Queue queue) {
		this.queue = queue;
	}
	@Override
	public Ready create() {
		return new QueueReady(queue, new SocketReady(queue.getSelector(), queue.allocator()));
	}
}
