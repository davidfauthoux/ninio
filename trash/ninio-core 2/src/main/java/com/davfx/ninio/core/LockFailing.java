package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Lock;

public final class LockFailing implements Failing {
	private final Lock<?, IOException> lock;
	
	public LockFailing(Lock<?, IOException> lock) {
		this.lock = lock;
	}
	
	@Override
	public void failed(IOException e) {
		lock.fail(e);
	}
}
