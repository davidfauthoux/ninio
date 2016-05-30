package com.davfx.ninio.core.v3.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.v3.ExecutorUtils;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.util.ClassThreadFactory;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class Timeout implements AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Timeout.class);
	
	private static final Config CONFIG = ConfigFactory.load(Timeout.class.getClassLoader());
	private static final double PRECISION = ConfigUtils.getDuration(CONFIG, "ninio.timeout.precision");

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(Timeout.class));
	
	private static final double now() {
		return System.currentTimeMillis() / 1000d;
	}
	
	private static final class Task {
		public long id = -1L;
		
		private final double timeout;
		
		public double time;
		public Failing failing = null;

		public Task(double timeout) {
			this.timeout = timeout;
		}
		
		public void reset() {
			double now = now();
			time = now + timeout;
			LOGGER.trace("[now = {}] Reset in {} -> at {} ms", (long) (now * 1000L), timeout, (long) (time * 1000d));
		}
	}
	
	private long nextId = 0L;
	private final Map<Long, Task> tasks = new HashMap<>();
	private double scheduledAt = Double.NaN;
	private ScheduledFuture<?> future = null;
	
	public Timeout() {
	}
	
	@Override
	public void close() {
		ScheduledFuture<?> f = future;
		if (f != null) {
			f.cancel(false);
		}
		
		LOGGER.trace("Closed");
		
		executor.execute(new Runnable() {
			@Override
			public void run() {
				if (future != null) {
					future.cancel(false);
					future = null;
				}
			}
		});
		ExecutorUtils.shutdown(executor);
	}
	
	private void reschedule(double at) {
		if (!Double.isNaN(scheduledAt) && (at >= scheduledAt)) {
			return;
		}
		scheduledAt = at;

		if (future != null) {
			future.cancel(false);
			future = null;
		}
		
		double now = now();

		if (Double.isNaN(scheduledAt)) {
			LOGGER.trace("[now = {}] Unscheduled", (long) (now * 1000L));
			return;
		}
		
		if (scheduledAt < now) {
			scheduledAt = now;
		}
		double t = scheduledAt - now + PRECISION;
		LOGGER.trace("[now = {}] Scheduled in {} seconds (precision = {}) -> at {} ms, in {} ms", (long) (now * 1000L), t, PRECISION, (long) (scheduledAt * 1000d), (long) (t * 1000d));
		
		future = executor.schedule(new Runnable() {
			@Override
			public void run() {
				future = null;
				scheduledAt = Double.NaN;
				
				double now = now();

				LOGGER.trace("[now = {}] Executing", (long) (now * 1000L));

				Iterator<Task> i = tasks.values().iterator();
				double next = Double.NaN;
				while (i.hasNext()) {
					Task task = i.next();
					if (task.time > now) {
						if (Double.isNaN(next)) {
							next = task.time;
						} else {
							next = Math.min(next, task.time);
						}
						continue;
					}
					
					if (task.failing != null) {
						task.failing.failed(new IOException("Timeout"));
					}
					i.remove();
				}
				
				reschedule(next);
			}
		}, (long) (t  * 1000d), TimeUnit.MILLISECONDS);
	}
	
	public static interface Manager {
		void run(Failing failing);
		void reset();
		void cancel();
	}
	
	public Manager set(double timeout) {
		final Task task = new Task(timeout);
		return new Manager() {
			@Override
			public void run(final Failing failing) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (task.id >= 0L) {
							return;
						}
						task.failing = failing;
						task.id = nextId;
						nextId++;
						tasks.put(task.id, task);
						task.reset();
						reschedule(task.time);
					}
				});
			}
			@Override
			public void reset() {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (task.id < 0L) {
							return;
						}
						task.reset();
					}
				});
			}
			
			@Override
			public void cancel() {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (task.id < 0L) {
							return;
						}
						tasks.remove(task.id);
					}
				});
			}
		};
	}
}
