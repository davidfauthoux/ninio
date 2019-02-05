package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.util.Wait;

public final class WaitClosedConnection implements Connection {
	private final Wait wait;
	private final Connection wrappee;
	
	public WaitClosedConnection(Wait wait, Connection wrappee) {
		this.wait = wait;
		this.wrappee = wrappee;
	}
	
	@Override
	public void connected(Address address) {
		wrappee.connected(address);
	}
	
	@Override
	public void closed() {
		wait.run();
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
