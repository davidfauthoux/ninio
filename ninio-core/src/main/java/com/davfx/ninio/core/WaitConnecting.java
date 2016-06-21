package com.davfx.ninio.core;

import com.davfx.ninio.util.Wait;

public final class WaitConnecting implements Connecting {
	private final Wait wait;
	
	public WaitConnecting(Wait wait) {
		this.wait = wait;
	}
	
	@Override
	public void connected(Connector connector, Address address) {
		wait.run();
	}
}
