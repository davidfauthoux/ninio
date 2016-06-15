package com.davfx.ninio.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClassThreadFactory implements ThreadFactory {
	private static final AtomicInteger NUMBER = new AtomicInteger(0);
	private final Class<?> source;
	private final String suffix;
	private final boolean daemon;
	
	public ClassThreadFactory(Class<?> source, String suffix, boolean daemon) {
		this.source = source;
		this.suffix = suffix;
		this.daemon = daemon;
	}

	public ClassThreadFactory(Class<?> source, String suffix) {
		this(source, suffix, false);
	}
	public ClassThreadFactory(Class<?> source, boolean daemon) {
		this(source, null, daemon);
	}
	public ClassThreadFactory(Class<?> source) {
		this(source, false);
	}
	
	@Override
	public Thread newThread(Runnable r) {
		Thread t = new Thread(r, source.getSimpleName() + ((suffix == null) ? "" : ("-" + suffix)) + "-" + NUMBER.getAndIncrement());
		if (daemon) {
			t.setDaemon(true);
		}
		return t;
	}
}
