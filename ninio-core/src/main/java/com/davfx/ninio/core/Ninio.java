package com.davfx.ninio.core;

import java.util.concurrent.Executor;

import com.davfx.ninio.util.SerialExecutor;

public final class Ninio implements AutoCloseable {
	private final InternalQueue internalQueue = new InternalQueue();
	private final SerialExecutor internalExecutor = new SerialExecutor(Ninio.class); // Only one thread for now
	
	private Ninio() {
	}
	
	@Override
	public void close() {
		internalQueue.close();
	}
	
	public static Ninio create() {
		return new Ninio();
	}
	
	public <T> T create(NinioBuilder<T> builder) {
		return builder.create(new NinioProvider() {
			@Override
			public Queue queue() {
				return internalQueue;
			}
			@Override
			public Executor executor() {
				return internalExecutor;
			}
		});
	}
}
