package com.davfx.ninio.core.v3;

public final class Ninio implements AutoCloseable {
	private final InternalQueue internalQueue = new InternalQueue();
	
	private Ninio() {
	}
	
	@Override
	public void close() {
		internalQueue.waitFor();
		internalQueue.close();
	}
	
	public static Ninio create() {
		return new Ninio();
	}
	
	public <T> T create(NinioBuilder<T> builder) {
		return builder.create(internalQueue);
	}
}
