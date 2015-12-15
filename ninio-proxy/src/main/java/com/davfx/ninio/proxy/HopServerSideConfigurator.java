package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;

public final class HopServerSideConfigurator implements ServerSideConfigurator {

	private final Queue queue;
	private final ProxyListener listener;
	private final Map<Address, ProxyClient> clients = new HashMap<>();

	public HopServerSideConfigurator(Queue queue, ProxyListener listener) {
		this.queue = queue;
		this.listener = listener;
	}

	@Override
	public ReadyFactory configure(Address address, String connecterType, DataInputStream in) throws IOException {
		Address proxyAddress = new Address(in.readUTF(), in.readInt());
		ProxyClient c = clients.get(proxyAddress);
		if (c == null) {
			c = new ProxyClient(proxyAddress, listener);
			clients.put(proxyAddress, c);
		}
		String underlyingType = in.readUTF();
		return c.of(queue, underlyingType);
	}
}
