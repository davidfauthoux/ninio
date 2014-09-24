package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.IOException;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ReadyFactory;

public class Forward {
	private Forward() {
	}

	public static ProxyUtils.ServerSideConfigurator forward(Address reproxyAddress) {
		final ProxyClient c = new ProxyClient(reproxyAddress);
		return new ProxyUtils.ServerSideConfigurator() {
			@Override
			public ReadyFactory configure(String connecterType, DataInputStream in) throws IOException {
				return c.of(connecterType);
			}
		};
	}
}
