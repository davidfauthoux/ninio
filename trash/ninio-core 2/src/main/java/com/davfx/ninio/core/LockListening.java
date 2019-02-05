package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Lock;

public final class LockListening implements Listening {
	private final Lock<?, IOException> lock;
	private final Listening wrappee;
	
	public LockListening(Lock<?, IOException> lock, Listening wrappee) {
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
