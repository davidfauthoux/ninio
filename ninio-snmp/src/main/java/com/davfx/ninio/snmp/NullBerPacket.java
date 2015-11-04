package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

public final class NullBerPacket implements BerPacket {
	private final ByteBuffer lengthBuffer = BerPacketUtils.lengthBuffer(0);

	public NullBerPacket() {
	}

	@Override
	public void write(ByteBuffer buffer) {
		BerPacketUtils.writeHeader(BerConstants.NULL, lengthBuffer, buffer);
	}

	@Override
	public ByteBuffer lengthBuffer() {
		return lengthBuffer;
	}

	@Override
	public int length() {
		return 0;
	}
}
