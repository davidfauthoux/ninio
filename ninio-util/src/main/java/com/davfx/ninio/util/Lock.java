package com.davfx.ninio.util;

public final class Lock<R, E extends Exception> {
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
			return;
		}
		this.result = result;
		done = true;
		notifyAll();
	}
	
	public synchronized void fail(E fail) {
		if (done) {
			return;
		}
		this.fail = fail;
		done = true;
		notifyAll();
	}
}
