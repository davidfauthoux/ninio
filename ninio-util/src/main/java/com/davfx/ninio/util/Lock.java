package com.davfx.ninio.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Lock<R, E extends Exception> {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Lock.class);
	
	private boolean done = false;
	private R result = null;
	private E fail = null;

	public Lock() {
	}
	
	public synchronized R waitFor() throws E {
		while (true) {
			if (fail != null) {
				throw fail;
			}
			if (done) {
				return result;
			}
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
	}
	
	public synchronized void set(R result) {
		if (done) {
			LOGGER.warn("Set multiple times (current result = {}, overwritten result = {}, fail = {})", this.result, result, fail, null);
		}
		this.result = result;
		done = true;
		notifyAll();
	}
	
	public synchronized void fail(E fail) {
		if (done) {
			LOGGER.warn("Failed multiple times (current fail = {}, overwritten fail = {}, result = {})", this.fail, fail, result, null);
		}
		this.fail = fail;
		done = true;
		notifyAll();
	}
}
