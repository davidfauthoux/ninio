package com.davfx.ninio.core;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.SerialExecutor;
import com.typesafe.config.Config;

public final class Ninio implements AutoCloseable {
	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(Ninio.class.getPackage().getName());
	private static final int THREADING = CONFIG.getInt("threading");
	private static final int MAX_QUEUE = CONFIG.getInt("queue.max");

	private final SerialExecutor[] internalExecutors = new SerialExecutor[THREADING];
	private final AtomicLong internalExecutorIndex = new AtomicLong(0L);

	private final InternalQueue[] internalQueues;

	private Ninio() {
		for (int i = 0; i < internalExecutors.length; i++) {
			internalExecutors[i] = new SerialExecutor(Ninio.class);
		}
		NinioPriority[] priorities = NinioPriority.values();
		internalQueues = new InternalQueue[Math.max(priorities.length, MAX_QUEUE)];
		for (int i = 0; i < internalQueues.length; i++) {
			internalQueues[i] = new InternalQueue(priorities[i % priorities.length]);
		}
	}
	
	@Override
	public void close() {
		for (InternalQueue internalQueue : internalQueues) {
			internalQueue.close();
		}
	}
	
	public static Ninio create() {
		return new Ninio();
	}
	
	public <T> T create(NinioBuilder<T> builder) {
		return builder.create(new NinioProvider() {
			@Override
			public Queue queue(NinioPriority priority) {
				return internalQueues[(int) (priority.ordinal() % internalQueues.length)];
			}
			@Override
			public Executor executor() {
				return internalExecutors[(internalExecutors.length == 1) ? 0 : ((int) (internalExecutorIndex.getAndIncrement() % internalExecutors.length))];
			}
		});
	}
}
