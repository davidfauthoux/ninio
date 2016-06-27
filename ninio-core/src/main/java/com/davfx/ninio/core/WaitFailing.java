package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Wait;

public final class WaitFailing implements Failing {
	private final Wait wait;
	
	public WaitFailing(Wait wait) {
		this.wait = wait;
	}
	
	@Override
	public void failed(IOException e) {
		wait.run();
	}
}
