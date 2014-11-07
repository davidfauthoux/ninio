package com.davfx.ninio.remote.ssh;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.remote.RemoteConnector;
import com.davfx.ninio.remote.RemoteConnectorFactory;
import com.davfx.ninio.ssh.SshClientConfigurator;

public final class SshRemoteConnectorFactory implements RemoteConnectorFactory {
	
	private final SshClientConfigurator configurator;
	
	public SshRemoteConnectorFactory(SshClientConfigurator configurator) {
		this.configurator = configurator;
	}
	
	@Override
	public RemoteConnector create(Address address) {
		return new SshRemoteConnector(configurator);
	}
}
