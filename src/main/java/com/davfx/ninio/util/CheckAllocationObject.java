package com.davfx.ninio.util;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckAllocationObject {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CheckAllocationObject.class);
	
	private static final class CountMax {
		public int count = 0;
		public int max = 0;
		public synchronized String inc() {
			count++;
			if (count > max) {
				max = count;
			}
			return count + " (max " + max + ")";
		}
		public synchronized String dec() {
			count--;
			return count + " (max " + max + ")";
		}
	}
	
	private static final Map<Class<?>, CountMax> COUNTS = new HashMap<>();

	private final String prefix;
	private final CountMax count;
	
	public CheckAllocationObject(Class<?> clazz) {
		prefix = clazz.getName();
		CountMax c;
		synchronized (COUNTS) {
			c = COUNTS.get(clazz);
			if (c == null) {
				c = new CountMax();
				COUNTS.put(clazz, c);
			}
		}
		count = c;
		
		String x = count.inc();
		LOGGER.debug("*** {} | Allocation inc: {}", prefix, x);
	}
	
	@Override
	protected void finalize() {
		String x = count.dec();
		LOGGER.debug("*** {} | Allocation dec: {}", prefix, x);
	}
}
