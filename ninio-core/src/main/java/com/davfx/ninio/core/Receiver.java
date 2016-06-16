package com.davfx.ninio.core;

import java.nio.ByteBuffer;

public interface Receiver {
	void received(Connector connector, Address address, ByteBuffer buffer);
}
