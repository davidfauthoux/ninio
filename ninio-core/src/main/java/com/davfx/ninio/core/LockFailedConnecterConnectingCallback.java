package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Lock;

public final class LockFailedConnecterConnectingCallback implements Connecter.Connecting.Callback {
	private final Lock<?, IOException> lock;
	private final Connecter.Connecting.Callback wrappee;
	
	public LockFailedConnecterConnectingCallback(Lock<?, IOException> lock, Connecter.Connecting.Callback wrappee) {
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
