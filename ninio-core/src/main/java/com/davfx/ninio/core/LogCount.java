package com.davfx.ninio.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LogCount implements Count {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(LogCount.class);
	
	private final String prefix;
	private int count = 0;
	private long bytes = 0L;
	private final long stepToLog;
	private double timestamp = 0d;
	private double topRate = 0d;
	
	public LogCount(String prefix, long stepToLog) {
		this.prefix = prefix;
		this.stepToLog = stepToLog;
	}
	
	@Override
	public void inc(long b) {
		count++;
		bytes += b;
		if (timestamp == 0d) {
			timestamp = System.currentTimeMillis() / 1000d;
		} else {
			if (bytes > stepToLog) {
				double now = System.currentTimeMillis() / 1000d;
				double delta = now - timestamp;
				double rate = Double.NaN;
				if (delta > 0d) {
					rate = bytes / delta;
					topRate = Math.max(topRate, rate);
				}
				LOGGER.debug("{}: {} bytes over {} packets ({} Bps, top {} Bps)", prefix, bytes, count, rate, topRate);
				timestamp = now;
				bytes = 0L;
				count = 0;
			}
		}
	}
}
