package com.davfx.ninio.core.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;

public interface Connector extends Disconnectable {
	Connector send(Address address, ByteBuffer buffer);
}
