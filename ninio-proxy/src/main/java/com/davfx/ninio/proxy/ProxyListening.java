package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.ConfigurableNinioBuilder;
import com.davfx.ninio.core.Connector;

public interface ProxyListening extends Closing {
	ConfigurableNinioBuilder<Connector, ?> create(Address address, String header);
}
