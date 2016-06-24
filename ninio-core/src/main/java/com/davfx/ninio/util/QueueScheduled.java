package com.davfx.ninio.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Queue;
import com.davfx.util.ClassThreadFactory;

public final class QueueScheduled {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(QueueScheduled.class);
	
	private static final ScheduledExecutorService REPEAT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(QueueScheduled.class, true));
	
	private QueueScheduled() {
	}
	
	private static final class InnerCloseable implements Closeable {
		private boolean closed = false;
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
						try {
							task.run();
						} catch (Throwable t) {
							LOGGER.error("Error in scheduled task", t);
						}
					}
				});
			}
		}, 0, (long) (repeatTime * 1000d), TimeUnit.MILLISECONDS);
		return closeable;
	}

	public static void run(final Queue queue, double time, final Runnable task) {
		REPEAT_EXECUTOR.schedule(new Runnable() {
			@Override
			public void run() {
				queue.post(task);
			}
		}, (long) (time * 1000d), TimeUnit.MILLISECONDS);
	}
}