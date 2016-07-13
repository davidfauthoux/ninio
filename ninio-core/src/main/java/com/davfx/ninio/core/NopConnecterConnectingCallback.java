package com.davfx.ninio.core;

import java.io.IOException;

public final class NopConnecterConnectingCallback implements Connecter.Connecting.Callback {
	public NopConnecterConnectingCallback() {
	}
	
	@Override
	public void sent() {
	}
	@Override
	public void failed(IOException ioe) {
	}
}
