package com.davfx.ninio.remote.telnet;

import java.io.IOException;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.remote.RemoteClientHandler;
import com.davfx.ninio.remote.RemoteConnector;
import com.davfx.ninio.remote.RemoteConnectorFactory;
import com.davfx.ninio.telnet.TelnetClient;
import com.davfx.ninio.telnet.TelnetClientConfigurator;
import com.davfx.ninio.telnet.TelnetClientHandler;

public final class TelnetRemoteConnectorFactory implements RemoteConnectorFactory {
	private final TelnetClientConfigurator configurator;
	
	public TelnetRemoteConnectorFactory(TelnetClientConfigurator configurator) {
		this.configurator = configurator;
	}
	
	@Override
	public RemoteConnector create(Address address) {
		final TelnetClient client = new TelnetClient(new TelnetClientConfigurator(configurator).withAddress(address));

		return new RemoteConnector() {
			@Override
			public void close() {
			}
			
			@Override
			public String getEol() {
				return TelnetClient.EOL;
			}
			
			@Override
			public void connect(final RemoteClientHandler clientHandler) {
				client.connect(new TelnetClientHandler() {
					@Override
					public void failed(IOException e) {
						clientHandler.failed(e);
					}
					@Override
					public void close() {
						clientHandler.close();
					}
					@Override
					public void received(String text) {
						clientHandler.received(text);
					}
					@Override
					public void launched(final Callback callback) {
						clientHandler.launched(new RemoteClientHandler.Callback() {
							@Override
							public void close() {
								callback.close();
							}
							@Override
							public void send(String text) {
								callback.send(text);
							}
						});
					}
				});
			}
		};
	}
}
