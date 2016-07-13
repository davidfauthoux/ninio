package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.util.Lock;

public final class LockReceivedConnecterCallback implements Connecter.Callback {
	private final Lock<ByteBuffer, ?> lock;
	private final Connecter.Callback wrappee;
	
	public LockReceivedConnecterCallback(Lock<ByteBuffer, ?> lock, Connecter.Callback wrappee) {
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
		wrappee.failed(ioe);
	}
	@Override
	public void received(Address address, ByteBuffer buffer) {
		lock.set(buffer);
		wrappee.received(address, buffer);
	}
}
