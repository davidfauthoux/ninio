package com.davfx.ninio.snmp.v3;

import java.nio.ByteBuffer;

public final class IntegerBerPacket implements BerPacket {
	private final ByteBuffer lengthBuffer;
	private final int length;
	private final int val;
	private final int m;

	public IntegerBerPacket(int value) {
		int mask = 0x1FF << ((8 * 3) - 1);
		int l = 4;
		while ((((value & mask) == 0) || ((value & mask) == mask)) && l > 1) {
			l--;
			value <<= 8;
		}
		mask = 0xFF << (8 * 3);

		length = l;
		val = value;
		m = mask;

		lengthBuffer = BerPacketUtils.lengthBuffer(length);
	}

	@Override
	public void write(ByteBuffer buffer) {
		BerPacketUtils.writeHeader(BerConstants.INTEGER, lengthBuffer, buffer);
		int v = val;
		for (int i = 0; i < length; i++) {
			buffer.put((byte) ((v & m) >>> (8 * 3)));
			v <<= 8;
		}
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
