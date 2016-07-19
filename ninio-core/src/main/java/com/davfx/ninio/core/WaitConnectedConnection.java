package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.util.Wait;

public final class WaitConnectedConnection implements Connection {
	private final Wait wait;
	private final Connection wrappee;
	
	public WaitConnectedConnection(Wait wait, Connection wrappee) {
		this.wait = wait;
		this.wrappee = wrappee;
	}
	
	@Override
	public void connected(Address address) {
		wait.run();
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
		wrappee.received(address, buffer);
	}
}
