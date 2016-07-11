package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.ConfigurableNinioBuilder;
import com.davfx.ninio.core.Connector;

public interface WithHeaderSocketBuilder extends ConfigurableNinioBuilder<Connector, WithHeaderSocketBuilder> {
	WithHeaderSocketBuilder header(String header);
	WithHeaderSocketBuilder with(Address address);
}