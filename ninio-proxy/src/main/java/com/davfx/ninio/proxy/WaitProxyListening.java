package com.davfx.ninio.proxy;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.util.Wait;

public final class WaitProxyListening implements ProxyListening {
	private final Wait wait;

	public WaitProxyListening(Wait wait) {
		this.wait = wait;
	}
	
	@Override
	public void closed() {
		wait.run();
	}
	
	@Override
	public void connected(Address address) {
	}
	@Override
	public void failed(IOException e) {
	}
	@Override
	public NinioBuilder<Connecter> create(Address address, String header) {
		return null;
	}
}
