package com.davfx.ninio.util;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;

public final class SerialExecutor implements Executor {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SerialExecutor.class);
	
	private static final Config CONFIG = ConfigUtils.load(new com.davfx.ninio.util.dependencies.Dependencies(), SerialExecutor.class);
	private static final double TIMEOUT_TO_SHUTDOWN_INTERNAL_THREAD = ConfigUtils.getDuration(CONFIG, "executor.serial.autoshutdown");
	
	private final Class<?> clazz;
	private Thread thread = null;
	
	private Runnable last = null;
	private List<Runnable> toExecute = null;
	
	public SerialExecutor(Class<?> clazz) {
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
							synchronized (SerialExecutor.this) {
								if (last == null) {
									try {
										SerialExecutor.this.wait((long) (TIMEOUT_TO_SHUTDOWN_INTERNAL_THREAD * 1000d));
									} catch (InterruptedException ie) {
									}
								}
								
								if (last == null) {
									thread = null;//TODO check rerun
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
							
							try {
								lastRunnable.run();
							} catch (Throwable t) {
								LOGGER.error("Error in threaded task", t);
							}
							if (listToExecute != null) {
								for (Runnable r : listToExecute) {
									try {
										r.run();
									} catch (Throwable t) {
										LOGGER.error("Error in threaded task", t);
									}
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
