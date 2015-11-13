package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ReadyFactory;

public final class ForwardServerSideConfigurator implements ServerSideConfigurator {
	private final ProxyClient client;
	
	public ForwardServerSideConfigurator(Address toAddress, ProxyListener listener) {
		client = new ProxyClient(toAddress, listener);
	}

	@Override
	public ReadyFactory configure(String connecterType, DataInputStream in) throws IOException {
		return client.of(connecterType);
	}
}
