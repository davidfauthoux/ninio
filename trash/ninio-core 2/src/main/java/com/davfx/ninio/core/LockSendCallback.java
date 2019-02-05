package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Lock;

public final class LockSendCallback implements SendCallback {
	private final Lock<?, IOException> lock;
	private final SendCallback wrappee;
	
	public LockSendCallback(Lock<?, IOException> lock, SendCallback wrappee) {
		this.lock = lock;
		this.wrappee = wrappee;
	}
	
	@Override
	public void sent() {
		wrappee.sent();
	}
	
	@Override
	public void failed(IOException ioe) {
		lock.fail(ioe);
		wrappee.failed(ioe);
	}
}
