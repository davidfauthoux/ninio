package com.davfx.ninio.core;

import java.nio.ByteBuffer;

public interface Receiver {
	void received(Address address, ByteBuffer buffer);
}
