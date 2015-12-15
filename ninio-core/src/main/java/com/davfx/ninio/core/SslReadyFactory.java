package com.davfx.ninio.core;

public final class SslReadyFactory implements ReadyFactory {
	private final Trust trust;
	private final Queue queue;
	public SslReadyFactory(Queue queue, Trust trust) {
		this.trust = trust;
		this.queue = queue;
	}
	@Override
	public Ready create() {
		return new QueueReady(queue, new SslReady(trust, queue.allocator(), new SocketReady(queue.getSelector(), queue.allocator())));
	}
}
