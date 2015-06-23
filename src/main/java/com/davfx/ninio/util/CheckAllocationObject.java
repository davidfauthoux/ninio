package com.davfx.ninio.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class CheckAllocationObject {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CheckAllocationObject.class);
	
	private static final boolean DISPLAY_INC = true;
	private static final boolean DISPLAY_DEC = true;
	private static final double DISPLAY_DEC_LIMIT = 1d * 60d;
	
	private static final class CountMax {
		private final List<Time> times = new ArrayList<>();
		public int count = 0;
		public int max = 0;
		public CountMax() {
		}
		public String inc(Time time) {
			int c;
			int m;
			synchronized (this) {
				times.add(time);
				count++;
				if (count > max) {
					max = count;
				}
				c = count;
				m = max;
			}
			if (!DISPLAY_INC) {
				return null;
			}
			return c + " (max " + m + ")";
		}
		public String dec(Time time) {
			int c;
			int m;
			long t;
			synchronized (this) {
				times.remove(time);
				count--;
				c = count;
				m = max;
				if (times.isEmpty()) {
					t = -1L;
				} else {
					t = times.get(0).timestamp;
				}
			}

			if (!DISPLAY_DEC) {
				return null;
			}

			if (t < 0L) {
				return c + " (max " + m + ")";
			}

			long delta = (System.currentTimeMillis() - t) / 1000L;
			if (delta < ((long) DISPLAY_DEC_LIMIT)) {
				return null;
			}
			long min = delta / 60L;
			long sec = delta - (min * 60L);
			return c + " (max " + m + ", oldest " + min + " min " + sec + " sec ago)";
		}
	}
	
	private static final class Time {
		public final long timestamp = System.currentTimeMillis();
		public Time() {
		}
	}
	
	private static final Map<Class<?>, CountMax> COUNTS = new HashMap<>();

	private final String prefix;
	private final CountMax count;
	private final Time time = new Time();
	
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
		
		String x = count.inc(time);
		if (x != null) {
			LOGGER.debug("*** {} | Allocation inc: {}", prefix, x);
		}
	}
	
	// Beware! Don't use this class in production!
	// http://stackoverflow.com/questions/8355064/is-memory-leak-why-java-lang-ref-finalizer-eat-so-much-memory
	@Override
	protected void finalize() {
		String x = count.dec(time);
		if (x != null) {
			LOGGER.debug("*** {} | Allocation dec: {}", prefix, x);
		}
	}
}
