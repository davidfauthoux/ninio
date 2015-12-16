package com.davfx.ninio.proxy;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;

final class BaseClientSide implements ClientSide {
	private static final Logger LOGGER = LoggerFactory.getLogger(BaseClientSide.class);

	private final Map<AddressConnecterTypeKey, ClientSideConfigurator> configurators = new HashMap<>(); // ConcurrentHashMap not necessary here because write() is always called from Queue

	public BaseClientSide() {
		configurators.put(new AddressConnecterTypeKey(null, ProxyCommons.Types.SOCKET), new EmptyClientSideConfiguration());
		configurators.put(new AddressConnecterTypeKey(null, ProxyCommons.Types.DATAGRAM), new EmptyClientSideConfiguration());
		configurators.put(new AddressConnecterTypeKey(null, ProxyCommons.Types.PING), new EmptyClientSideConfiguration());
		configurators.put(new AddressConnecterTypeKey(null, ProxyCommons.Types.HOP), new EmptyClientSideConfiguration());
	}
	
	@Override
	public void override(Address address, String type, ClientSideConfigurator configurator) {
		configurators.put(new AddressConnecterTypeKey(address, type), configurator);
	}
	
	@Override
	public void write(Address address, String connecterType, DataOutputStream out) throws IOException {
		out.writeUTF(connecterType);
		ClientSideConfigurator configurator = configurators.get(new AddressConnecterTypeKey(address, connecterType));
		if (configurator == null) {
			configurator = configurators.get(new AddressConnecterTypeKey(new Address(null, address.getPort()), connecterType));
		}
		if (configurator == null) {
			configurator = configurators.get(new AddressConnecterTypeKey(null, connecterType));
		}
		if (configurator == null) {
			LOGGER.error("Unknown type: {}", connecterType);
			throw new IOException("Unknown type: " + connecterType);
		}
		configurator.configure(address, connecterType, out);
	}
}
