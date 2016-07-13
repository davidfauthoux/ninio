package com.davfx.ninio.core;

import java.io.IOException;

import com.davfx.ninio.util.Wait;

public final class WaitSentConnecterConnectingCallback implements Connecter.Connecting.Callback {
	private final Wait wait;
	private final Connecter.Connecting.Callback wrappee;
	
	public WaitSentConnecterConnectingCallback(Wait wait, Connecter.Connecting.Callback wrappee) {
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
