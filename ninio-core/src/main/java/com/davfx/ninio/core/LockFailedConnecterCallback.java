package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.util.Lock;

public final class LockFailedConnecterCallback implements Connecter.Callback {
	private final Lock<?, IOException> lock;
	private final Connecter.Callback wrappee;
	
	public LockFailedConnecterCallback(Lock<?, IOException> lock, Connecter.Callback wrappee) {
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
	public void received(Address address, ByteBuffer buffer) {
		wrappee.received(address, buffer);
	}
}
