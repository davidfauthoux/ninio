package com.davfx.ninio.proxy;

import java.io.DataOutputStream;
import java.io.IOException;

public interface ClientSide {
	void override(String type, ClientSideConfigurator configurator);
	void write(String connecterType, DataOutputStream out) throws IOException;
}