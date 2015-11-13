package com.davfx.ninio.proxy;

import java.io.DataOutputStream;
import java.io.IOException;

public interface ClientSideConfigurator {
	void configure(String connecterType, DataOutputStream out) throws IOException;
}