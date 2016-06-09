package com.davfx.ninio.core.v3;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ClassThreadFactory;

public final class Limit implements AutoCloseable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Limit.class);

	private final ExecutorService executor = Executors.newSingleThreadExecutor(new ClassThreadFactory(Timeout.class));
	
	private static final class Task {
		public long id = -1L;
		
		private final List<Runnable> runnables = new LinkedList<>();
		private boolean canceled = false;
		private boolean running = false;

		public Task() {
		}
	}

	private final int max;
	
	private long nextId = 0L;
	private final Map<Long, Task> tasks = new LinkedHashMap<>();
	
	private int currentlyRunning = 0;
	
	public Limit(int max) {
		if (max < 0) {
			throw new IllegalArgumentException();
		}
		this.max = max;
	}
	
	@Override
	public void close() {
		ExecutorUtils.shutdown(executor);
	}
	
	public static interface Manager {
		void add(Runnable runnable);
		void cancel();
	}
	
	public Manager inc() {
		if (max == 0) {
			return new Manager() {
				@Override
				public void cancel() {
				}
				@Override
				public void add(Runnable runnable) {
					runnable.run();
				}
			};
		}
		
		final Task task = new Task();
		return new Manager() {
			@Override
			public void add(final Runnable runnable) {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (task.canceled) {
							return;
						}
						
						if (!task.running && (currentlyRunning < max)) {
							task.running = true;
							currentlyRunning++;
							LOGGER.trace("Task immediately running, now running {}", currentlyRunning);
						}
						
						if (task.running) {
							runnable.run();
							return;
						}

						if (task.id < 0L) {
							task.id = nextId;
							nextId++;
							tasks.put(task.id, task);
							LOGGER.trace("Waiting task registered: {}", task.id);
						}
						task.runnables.add(runnable);
					}
				});
			}
			
			@Override
			public void cancel() {
				executor.execute(new Runnable() {
					@Override
					public void run() {
						if (task.canceled) {
							return;
						}
						
						task.canceled = true;

						if (task.running) {
							currentlyRunning--;
							
							if (currentlyRunning < 0) {
								throw new IllegalStateException();
							}

							LOGGER.trace("Task canceled (id = {}), now running {}", task.id, currentlyRunning);

							if (!tasks.isEmpty()) {
								Iterator<Task> i = tasks.values().iterator();
								Task t = i.next();
								i.remove();

								LOGGER.trace("Running waiting task: {}", t.id);

								t.running = true;
								currentlyRunning++;

								if (currentlyRunning > max) {
									throw new IllegalStateException();
								}

								for (Runnable r : t.runnables) {
									r.run();
								}
								t.runnables.clear();
							}
						} else {
							if (task.id >= 0L) {
								LOGGER.trace("Limited task canceled without having been run: {}", task.id);
								tasks.remove(task.id);
							} else {
								LOGGER.trace("Limited task canceled without having been used");
							}
						}
					}
				});
			}
		};
	}
}
