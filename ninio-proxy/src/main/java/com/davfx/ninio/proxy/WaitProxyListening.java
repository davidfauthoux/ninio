package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ConfigurableNinioBuilder;
import com.davfx.ninio.core.Connector;
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
	public ConfigurableNinioBuilder<Connector, ?> create(Address address, String header) {
		return null;
	}
}
