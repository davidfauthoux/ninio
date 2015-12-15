package com.davfx.ninio.core;

public final class CountingBytesReadyFactory implements ReadyFactory {
	private final Count readCount;
	private final Count writeCount;
	private final ReadyFactory wrappee;
	public CountingBytesReadyFactory(Count readCount, Count writeCount, ReadyFactory wrappee) {
		this.readCount = readCount;
		this.writeCount = writeCount;
		this.wrappee = wrappee;
	}
	@Override
	public Ready create() {
		return new CountingBytesReady(readCount, writeCount, wrappee.create());
	}
}
