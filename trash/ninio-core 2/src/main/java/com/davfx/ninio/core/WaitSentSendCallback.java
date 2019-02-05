package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Wait;

public final class WaitSentSendCallback implements SendCallback {
	private final Wait wait;
	private final SendCallback wrappee;
	
	public WaitSentSendCallback(Wait wait, SendCallback wrappee) {
		this.wait = wait;
		this.wrappee = wrappee;
	}
	
	@Override
	public void sent() {
		wait.run();
		wrappee.sent();
	}
	
	@Override
	public void failed(IOException ioe) {
		wrappee.failed(ioe);
	}
}
