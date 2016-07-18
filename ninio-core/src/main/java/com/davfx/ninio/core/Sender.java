package com.davfx.ninio.core;

import java.nio.ByteBuffer;

public interface Sender {
	void send(Address address, ByteBuffer buffer, SendCallback callback);
}
