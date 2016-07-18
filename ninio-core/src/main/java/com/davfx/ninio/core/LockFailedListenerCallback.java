package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Lock;

public final class LockFailedListenerCallback implements Listener.Callback {
	private final Lock<?, IOException> lock;
	private final Listener.Callback wrappee;
	
	public LockFailedListenerCallback(Lock<?, IOException> lock, Listener.Callback wrappee) {
		this.lock = lock;
		this.wrappee = wrappee;
	}
	
	@Override
	public void connected(Address address) {
		wrappee.connected(address);
	}
	
	@Override
	public void closed() {
		wrappee.closed();
	}
	@Override
	public void failed(IOException ioe) {
		lock.fail(ioe);
		wrappee.failed(ioe);
	}
	@Override
	public Connection connecting(Connected connecting) {
		return wrappee.connecting(connecting);
	}
}
