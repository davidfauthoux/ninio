package com.davfx.ninio.core;

public final class CountingCurrentOpenReadyFactory implements ReadyFactory {
	private final Count openCount;
	private final ReadyFactory wrappee;
	public CountingCurrentOpenReadyFactory(Count openCount, ReadyFactory wrappee) {
		this.openCount = openCount;
		this.wrappee = wrappee;
	}
	@Override
	public Ready create() {
		return new CountingCurrentOpenReady(openCount, wrappee.create());
	}
}
