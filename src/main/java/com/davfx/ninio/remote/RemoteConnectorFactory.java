package com.davfx.ninio.remote;

import com.davfx.ninio.common.Address;

public interface RemoteConnectorFactory {
	RemoteConnector create(Address address);
}
