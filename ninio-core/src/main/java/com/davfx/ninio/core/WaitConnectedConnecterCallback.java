package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.util.Wait;

public final class WaitConnectedConnecterCallback implements Connecter.Callback {
	private final Wait wait;
	private final Connecter.Callback wrappee;
	
	public WaitConnectedConnecterCallback(Wait wait, Connecter.Callback wrappee) {
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
