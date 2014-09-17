package com.davfx.ninio.proxy;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ReadyFactory;

public final class ProxyClient {
	private final ProxyReady proxyReady;

	public ProxyClient(Address proxyServerAddress) {
		proxyReady = new ProxyReady(proxyServerAddress);
	}

	public ReadyFactory socket() {
		return new ProxyReadyFactory(proxyReady, ProxyUtils.SOCKET_TYPE);
	}
	public ReadyFactory datagram() {
		return new ProxyReadyFactory(proxyReady, ProxyUtils.DATAGRAM_TYPE);
	}

	public ProxyClient override(String type, ProxyUtils.ClientSideConfigurator configurator) {
		proxyReady.override(type, configurator);
		return this;
	}
	public ReadyFactory of(String type) {
		return new ProxyReadyFactory(proxyReady, type);
	}
}
