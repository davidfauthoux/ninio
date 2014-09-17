package com.davfx.ninio.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Threads {
	private static final Logger LOGGER = LoggerFactory.getLogger(Threads.class);
	private Threads() {
	}
	
	public static void run(Class<?> from, Runnable r) {
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					r.run();
				} catch (Throwable t) {
					LOGGER.error("Fatal error", t);
				}
			}
		});
		t.setName(from.getCanonicalName());
		t.start();
	}
}
