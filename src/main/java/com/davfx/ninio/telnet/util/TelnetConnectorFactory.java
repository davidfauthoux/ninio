package com.davfx.ninio.telnet.util;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.telnet.TelnetConnector;

public interface TelnetConnectorFactory {
	TelnetConnector create(Address address);
}
