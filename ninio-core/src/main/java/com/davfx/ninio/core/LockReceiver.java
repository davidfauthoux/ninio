package com.davfx.ninio.core;

import java.nio.ByteBuffer;

import com.davfx.ninio.util.Lock;

public final class LockReceiver implements Receiver {
	private final Lock<ByteBuffer, ?> lock;
	
	public LockReceiver(Lock<ByteBuffer, ?> lock) {
		this.lock = lock;
	}
	
	@Override
	public void received(Connector connector, Address address, ByteBuffer buffer) {
		lock.set(buffer);
	}
}
