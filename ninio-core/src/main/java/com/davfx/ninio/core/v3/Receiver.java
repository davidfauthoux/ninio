package com.davfx.ninio.core.v3;

import java.nio.ByteBuffer;

public interface Receiver {
	void received(Connector connector, Address address, ByteBuffer buffer);
}
