package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.IOException;

import com.davfx.ninio.core.ReadyFactory;

public final class SimpleServerSideConfigurator implements ServerSideConfigurator {
	private final ReadyFactory readyFactory;
	public SimpleServerSideConfigurator(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
	}
	
	@Override
	public ReadyFactory configure(String connecterType, DataInputStream in) throws IOException {
		return readyFactory;
	}
}