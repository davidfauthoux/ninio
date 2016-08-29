package com.davfx.ninio.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.DateUtils;
import com.davfx.ninio.util.SerialExecutor;
import com.typesafe.config.Config;

public final class Timeout implements AutoCloseable {

	private static final Logger LOGGER = LoggerFactory.getLogger(Timeout.class);
	
	private static final Config CONFIG = ConfigUtils.load(new com.davfx.ninio.core.dependencies.Dependencies(), Timeout.class);
	private static final double PRECISION = ConfigUtils.getDuration(CONFIG, "timeout.precision");

	private final Executor executor = new SerialExecutor(Timeout.class);
	
	private static final class Task {
		public long id = -1L;
		
		private final double timeout;
		
		public double time;
		public Runnable failing = null;

		public Task(double timeout) {
			this.timeout = timeout;
		}
		
		public void reset() {
			double now = DateUtils.now();
			time = now + timeout;
			LOGGER.trace("[now = {}] Reset in {} -> at {} ms", (long) (now * 1000L), timeout, (long) (time * 1000d));
		}
	}
	
	private long nextId = 0L;
	private final Map<Long, Task> tasks = new HashMap<>();
	private boolean closed = false;
	
	public Timeout() {
		executeWait();
	}
	
	@Override
	public void close() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				closed = true;
			}
		});
	}
	
	private void executeWait() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep((long) (PRECISION * 1000d));
				} catch (InterruptedException ie) {
				}

				if (!closed) {
					executeCheck();
				}
			}
		});
	}
	private void executeCheck() {
		executor.execute(new Runnable() {
			@Override
			public void run() {
				double now = DateUtils.now();

				Iterator<Task> i = tasks.values().iterator();
				while (i.hasNext()) {
					Task task = i.next();
					if (task.time <= now) {
						if (task.failing != null) {
							task.failing.run();
						}
						i.remove();
					}
				}
				
				if (!closed) {
					executeWait();
				}
			}
		});
	}
	
	public static interface Manager {
		void run(Runnable failing);
		void reset();
		void cancel();
	}
	
	public Manager set(double timeout) {
		final Task task = new Task(timeout);
		return new Manager() {
			@Override
			public void run(final Runnable failing) {
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
