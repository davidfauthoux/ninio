package com.davfx.ninio.snmp.v3;

import java.nio.ByteBuffer;

public interface BerPacket {
	int length();
	ByteBuffer lengthBuffer();
	void write(ByteBuffer buffer);
}
