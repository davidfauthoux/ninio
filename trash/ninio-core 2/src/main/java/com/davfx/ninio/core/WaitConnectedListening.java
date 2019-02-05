package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Wait;

public final class WaitConnectedListening implements Listening {
	private final Wait wait;
	private final Listening wrappee;
	
	public WaitConnectedListening(Wait wait, Listening wrappee) {
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
	public Connection connecting(Connected connecting) {
		return wrappee.connecting(connecting);
	}
}
