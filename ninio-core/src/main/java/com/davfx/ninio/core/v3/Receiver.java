package com.davfx.ninio.core.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;

public interface Receiver {
	void received(Connector connector, Address address, ByteBuffer buffer);
}
