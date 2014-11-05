package com.davfx.ninio.common;

public final class LogReadyFactory implements ReadyFactory {
	private final ReadyFactory factory;
	private final LogReady.Scheduled scheduled;

	public LogReadyFactory(String id, ReadyFactory factory) {
		this.factory = factory;
		scheduled = new LogReady.Scheduled(id);
	}
	
	@Override
	public Ready create(Queue queue, ByteBufferAllocator allocator) {
		return new LogReady(scheduled, factory.create(queue, allocator));
	}
}
