package com.davfx.ninio.proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Throttle {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Throttle.class);
	
	private final double bytesPerSecond;
	private final double timeStep;
	private final long bytesStep;
	private long count = 0L;
	private long timestamp = System.currentTimeMillis();
	
	public Throttle(double bytesPerSecond, double timeStep, long bytesStep) {
		LOGGER.debug("Will throttle at {} bps", bytesPerSecond);
		this.bytesPerSecond = bytesPerSecond;
		this.timeStep = timeStep;
		this.bytesStep = bytesStep;
	}

	public void sent(int len) {
		count += len;

		double deltaTime = (System.currentTimeMillis() - timestamp) / 1000d;
		if ((deltaTime >= timeStep) || (count >= bytesStep)) {
			double timeToSendCount = count / bytesPerSecond;
			LOGGER.trace("Have sent {} bytes in {} seconds (should be: {} seconds)", count, deltaTime, timeToSendCount);
			double timeToWait = timeToSendCount - deltaTime;
			LOGGER.trace("Throttling {} seconds", timeToWait);
			if (timeToWait > 0d) {
				try {
					Thread.sleep((long) Math.ceil(timeToWait * 1000d));
				} catch (InterruptedException e) {
				}
			}
			count = 0L;
			timestamp = System.currentTimeMillis();
		}
	}
}
