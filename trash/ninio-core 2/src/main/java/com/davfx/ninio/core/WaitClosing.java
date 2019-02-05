package com.davfx.ninio.core;

import com.davfx.ninio.util.Wait;

public final class WaitClosing implements Closing {
	private final Wait wait;
	
	public WaitClosing(Wait wait) {
		this.wait = wait;
	}
	
	@Override
	public void closed() {
		wait.run();
	}
}
