package com.davfx.ninio.core.v3;

import java.nio.ByteBuffer;

public interface Connector extends Disconnectable {
	Connector send(Address address, ByteBuffer buffer);
}
