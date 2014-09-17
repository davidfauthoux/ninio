package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

public final class BytesBerPacket implements BerPacket {
	private final ByteBuffer lengthBuffer;
	private final int length;
	private final ByteBuffer s;

	public BytesBerPacket(ByteBuffer s) {
		this.s = s;
		length = s.remaining();
		lengthBuffer = BerPacketUtils.lengthBuffer(length);
	}

	@Override
	public void write(ByteBuffer buffer) {
		BerPacketUtils.writeHeader(BerConstants.OCTETSTRING, lengthBuffer, buffer);
		buffer.put(s);
	}

	@Override
	public ByteBuffer lengthBuffer() {
		return lengthBuffer;
	}

	@Override
	public int length() {
		return length;
	}
}
