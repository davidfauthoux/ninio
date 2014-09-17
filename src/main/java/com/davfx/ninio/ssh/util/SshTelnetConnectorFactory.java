package com.davfx.ninio.ssh.util;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.telnet.TelnetConnector;
import com.davfx.ninio.telnet.util.TelnetConnectorFactory;

public final class SshTelnetConnectorFactory implements TelnetConnectorFactory {
	public SshTelnetConnectorFactory() {
	}
	@Override
	public TelnetConnector create(Address address) {
		return new SshTelnetConnector();
	}
}
