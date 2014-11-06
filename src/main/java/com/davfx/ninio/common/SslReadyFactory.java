package com.davfx.ninio.common;

public final class SslReadyFactory implements ReadyFactory {
	private final Trust trust;
	public SslReadyFactory(Trust trust) {
		this.trust = trust;
	}
	@Override
	public Ready create(Queue queue) {
		return new QueueReady(queue, new SslReady(trust, queue.allocator(), new SocketReady(queue.getSelector(), queue.allocator())));
	}
}
