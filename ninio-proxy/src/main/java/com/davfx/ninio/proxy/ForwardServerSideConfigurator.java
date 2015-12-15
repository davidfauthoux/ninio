package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;

public final class ForwardServerSideConfigurator implements ServerSideConfigurator {
	private final Queue queue;
	private final ProxyClient client;
	
	public ForwardServerSideConfigurator(Queue queue, Address toAddress, ProxyListener listener) {
		this.queue = queue;
		client = new ProxyClient(toAddress, listener);
	}

	@Override
	public ReadyFactory configure(Address address, String connecterType, DataInputStream in) throws IOException {
		return client.of(queue, connecterType);
	}
}
