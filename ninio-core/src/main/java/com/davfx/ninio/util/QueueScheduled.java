package com.davfx.ninio.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Queue;

public final class QueueScheduled {
	private static final ScheduledExecutorService REPEAT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(QueueScheduled.class, true));
	
	private QueueScheduled() {
	}
	
	private static final class InnerCloseable implements Closeable {
		private volatile boolean closed = false;
		@Override
		public void close() {
			closed = true;
		}
	}
	
	public static Closeable schedule(final Queue queue, double repeatTime, final Runnable task) {
		final InnerCloseable closeable = new InnerCloseable();
		REPEAT_EXECUTOR.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				if (closeable.closed) {
					throw new RuntimeException("Stop requested");
				}

				queue.post(new Runnable() {
					@Override
					public void run() {
						if (closeable.closed) {
							return;
						}
						task.run();
					}
				});
			}
		}, 0, (long) (repeatTime * 1000d), TimeUnit.MILLISECONDS);
		return closeable;
	}
}
