package com.davfx.ninio.core;

public final class DatagramReadyFactory implements ReadyFactory {
	public DatagramReadyFactory() {
	}
	@Override
	public Ready create(Queue queue) {
		return new QueueReady(queue, new DatagramReady(queue.getSelector(), queue.allocator()));
	}
}
