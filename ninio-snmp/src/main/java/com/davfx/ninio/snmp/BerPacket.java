package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

public interface BerPacket {
	int length();
	ByteBuffer lengthBuffer();
	void write(ByteBuffer buffer);
}
