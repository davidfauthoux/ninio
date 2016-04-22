package com.davfx.ninio.core.v3;

import com.davfx.ninio.core.Address;

public interface ConnectorFactory {
	Connector create(Address address);
}
