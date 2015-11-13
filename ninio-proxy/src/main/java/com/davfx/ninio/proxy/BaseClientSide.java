package com.davfx.ninio.proxy;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BaseClientSide implements ClientSide {
	private static final Logger LOGGER = LoggerFactory.getLogger(BaseClientSide.class);

	private final Map<String, ClientSideConfigurator> configurators = new HashMap<>(); // ConcurrentHashMap not necessary here because write() is always called from Queue

	public BaseClientSide() {
		configurators.put(ProxyCommons.Types.SOCKET, new EmptyClientSideConfiguration());
		configurators.put(ProxyCommons.Types.DATAGRAM, new EmptyClientSideConfiguration());
		configurators.put(ProxyCommons.Types.PING, new EmptyClientSideConfiguration());
		configurators.put(ProxyCommons.Types.HOP, new EmptyClientSideConfiguration());
	}
	
	@Override
	public void override(String type, ClientSideConfigurator configurator) {
		configurators.put(type, configurator);
	}
	
	@Override
	public void write(String connecterType, DataOutputStream out) throws IOException {
		out.writeUTF(connecterType);
		ClientSideConfigurator configurator = configurators.get(connecterType);
		if (configurator == null) {
			LOGGER.error("Unknown type: {}", connecterType);
			throw new IOException("Unknown type: " + connecterType);
		}
		configurator.configure(connecterType, out);
	}
}
