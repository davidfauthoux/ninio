package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Lock;

public final class LockClosing implements Closing {
	private final Lock<?, IOException> lock;
	
	public LockClosing(Lock<?, IOException> lock) {
		this.lock = lock;
	}
	
	@Override
	public void closed() {
		lock.fail(new IOException("Closed"));
	}
}
