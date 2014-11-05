package com.davfx.ninio.common;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Threads {
	private static final Logger LOGGER = LoggerFactory.getLogger(Threads.class);
	
	private static final AtomicInteger NAME_SUFFIX = new AtomicInteger(0);
	
	private Threads() {
	}
	
	//TODO Useless class?? use Executors single thread??
	public static long run(Class<?> from, final Runnable r) {
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					r.run();
				} catch (Throwable t) {
					LOGGER.error("Fatal error", t);
				}
			}
		});
		String name = from.getCanonicalName() + NAME_SUFFIX.getAndIncrement();
		t.setName(name);
		t.start();
		return t.getId();
	}
}
