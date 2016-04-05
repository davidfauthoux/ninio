package com.davfx.ninio.core.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;

@Deprecated
public interface Sender {
	void send(Address address, ByteBuffer buffer);
}
