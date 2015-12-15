package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.ReadyFactory;

public interface ServerSide extends AutoCloseable, Closeable {
	void override(Address address, String connecterType, ServerSideConfigurator configurator);
	ReadyFactory read(Address address, DataInputStream in) throws IOException;
}