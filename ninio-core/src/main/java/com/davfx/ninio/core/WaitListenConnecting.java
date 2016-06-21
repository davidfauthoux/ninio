package com.davfx.ninio.core;

import com.davfx.ninio.util.Wait;

public final class WaitListenConnecting implements ListenConnecting {
	private final Wait wait;
	
	public WaitListenConnecting(Wait wait) {
		this.wait = wait;
	}
	
	@Override
	public void connected(Disconnectable disconnectable) {
		wait.run();
	}
}
