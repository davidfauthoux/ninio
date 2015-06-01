package com.davfx.ninio.util;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckAllocationObject {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CheckAllocationObject.class);
	
	private static final Map<Class<?>, AtomicInteger> COUNTS = new HashMap<>();

	private final String prefix;
	private final AtomicInteger count;
	
	public CheckAllocationObject(Class<?> clazz) {
		prefix = clazz.getName();
		AtomicInteger c;
		synchronized (COUNTS) {
			c = COUNTS.get(clazz);
			if (c == null) {
				c = new AtomicInteger();
				COUNTS.put(clazz, c);
			}
		}
		count = c;
		
		int x = count.incrementAndGet();
		LOGGER.debug("*** {} | Allocation inc: {}", prefix, x);
	}
	
	@Override
	protected void finalize() {
		int x = count.decrementAndGet();
		LOGGER.debug("*** {} | Allocation dec: {}", prefix, x);
	}
}
