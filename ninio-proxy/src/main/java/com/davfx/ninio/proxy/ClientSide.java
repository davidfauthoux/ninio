package com.davfx.ninio.proxy;

import java.io.DataOutputStream;
import java.io.IOException;

import com.davfx.ninio.core.Address;

public interface ClientSide {
	void override(Address address, String type, ClientSideConfigurator configurator);
	void write(Address address, String connecterType, DataOutputStream out) throws IOException;
}