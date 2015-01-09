package com.davfx.ninio.common;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ClassThreadFactory implements ThreadFactory {
	private static final AtomicInteger NUMBER = new AtomicInteger(0);
	private final Class<?> source;
	private final String suffix;
	
	public ClassThreadFactory(Class<?> source, String suffix) {
		this.source = source;
		this.suffix = suffix;
	}

	public ClassThreadFactory(Class<?> source) {
		this(source, null);
	}
	
	@Override
	public Thread newThread(Runnable r) {
		return new Thread(r, source.getSimpleName() + ((suffix == null) ? "" : ("-" + suffix)) + "-" + NUMBER.getAndIncrement());
	}
}
