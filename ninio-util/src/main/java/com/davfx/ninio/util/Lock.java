package com.davfx.ninio.util;

public final class Lock<R, E extends Exception> {
	private R result = null;
	private E fail = null;

	public Lock() {
	}
	
	public synchronized R waitFor() throws Exception {
		while (true) {
			if (result != null) {
				return result;
			}
			if (fail != null) {
				throw fail;
			}
			try {
				wait();
			} catch (InterruptedException e) {
			}
		}
	}
	
	public synchronized void set(R result) {
		if (this.result != null) {
			return;
		}
		if (fail != null) {
			return;
		}
		this.result = result;
		notifyAll();
	}
	
	public synchronized void fail(E fail) {
		if (result != null) {
			return;
		}
		if (this.fail != null) {
			return;
		}
		this.fail = fail;
		notifyAll();
	}
}
