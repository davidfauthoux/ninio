package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Wait;

public final class WaitClosedListenerCallback implements Listener.Callback {
	private final Wait wait;
	private final Listener.Callback wrappee;
	
	public WaitClosedListenerCallback(Wait wait, Listener.Callback wrappee) {
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
	public Connection connecting(Connected connecting) {
		return wrappee.connecting(connecting);
	}
}
