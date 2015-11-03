package com.davfx.ninio.core;

public final class CountingReadyFactory implements ReadyFactory {
	private final Count readCount;
	private final Count writeCount;
	private final ReadyFactory wrappee;
	public CountingReadyFactory(Count readCount, Count writeCount, ReadyFactory wrappee) {
		this.readCount = readCount;
		this.writeCount = writeCount;
		this.wrappee = wrappee;
	}
	@Override
	public Ready create(Queue queue) {
		return new CountingReady(readCount, writeCount, wrappee.create(queue));
	}
}
