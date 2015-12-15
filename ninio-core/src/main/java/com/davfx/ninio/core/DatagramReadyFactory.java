package com.davfx.ninio.core;

public final class DatagramReadyFactory implements ReadyFactory {
	private final Queue queue;
	public DatagramReadyFactory(Queue queue) {
		this.queue = queue;
	}
	@Override
	public Ready create() {
		return new QueueReady(queue, new DatagramReady(queue.getSelector(), queue.allocator()));
	}
}
