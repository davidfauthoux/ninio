package com.davfx.ninio.common;

public final class SslReadyFactory implements ReadyFactory {
	private final Trust trust;
	public SslReadyFactory(Trust trust) {
		this.trust = trust;
	}
	@Override
	public Ready create(Queue queue, ByteBufferAllocator allocator) {
		return new QueueReady(queue, new SslReady(trust, new SocketReady(queue.getSelector(), allocator)));
	}
}
