package com.davfx.ninio.proxy;

import java.io.DataInputStream;
import java.io.IOException;

import com.davfx.ninio.core.ReadyFactory;

public interface ServerSideConfigurator {
	ReadyFactory configure(String connecterType, DataInputStream in) throws IOException;
}