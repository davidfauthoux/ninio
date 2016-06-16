package com.davfx.ninio.core;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import com.davfx.ninio.util.ClassThreadFactory;

public final class ThreadingSerialExecutor implements Executor {
	private static final double TIMEOUT_TO_SHUTDOWN_INTERNAL_THREAD = 10d;
	
	private final Class<?> clazz;
	private Thread thread = null;
	
	private Runnable last = null;
	private List<Runnable> toExecute = null;
	
	public ThreadingSerialExecutor(Class<?> clazz) {
		this.clazz = clazz;
	}
	
	@Override
	public void execute(Runnable runnable) {
		synchronized (this) {
			if (thread == null) {
				thread = new ClassThreadFactory(clazz, true).newThread(new Runnable() {
					@Override
					public void run() {
						while (true) {
							Runnable lastRunnable;
							List<Runnable> listToExecute;
							synchronized (ThreadingSerialExecutor.this) {
								if (last == null) {
									try {
										ThreadingSerialExecutor.this.wait((long) (TIMEOUT_TO_SHUTDOWN_INTERNAL_THREAD * 1000d));
									} catch (InterruptedException ie) {
									}
								}
								
								if (last == null) {
									thread = null;
									break;
								}
								
								lastRunnable = last;
								last = null;
								if (toExecute == null) {
									listToExecute = null;
								} else {
									listToExecute = toExecute;
									toExecute = null;
								}
							}
							
							lastRunnable.run();
							if (listToExecute != null) {
								for (Runnable r : listToExecute) {
									r.run();
								}
							}
						}
					}
				});
				thread.start();
			}
			
			if (last == null) {
				last = runnable;
			} else {
				if (toExecute == null) {
					toExecute = new LinkedList<>();
				}
				toExecute.add(runnable);
			}
			
			notifyAll();
		}
	}
}
