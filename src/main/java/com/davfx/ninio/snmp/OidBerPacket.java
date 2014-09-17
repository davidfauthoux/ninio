package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

public final class OidBerPacket implements BerPacket {
	private static final int MAX_OID_BUFFER_SIZE = 1024;

	private final ByteBuffer lengthBuffer;
	private final int length;
	private final ByteBuffer bb;

	public OidBerPacket(Oid oid) {
		int[] raw = oid.getRaw();

		if (raw.length < 2) {
			throw new IllegalArgumentException();
		}

		bb = ByteBuffer.allocate(MAX_OID_BUFFER_SIZE);
		bb.put((byte) ((raw[1] + (raw[0] * 40)) & 0xFF));

		for (int i = 2; i < raw.length; i++) {
			int value = raw[i];

			int mask = ~0x80;
			int bits = 0;

			while (true) {
				mask <<= 7;
				if ((value & mask) == 0) {
					break;
				}
				bits += 7;
			}

			while (bits >= 0) {
				int b = (value >>> bits) & ~0x80;
				if (bits > 0) {
					b |= 0x80; // Continuation bit
				}
				bb.put((byte) b);
				bits -= 7;
			}
		}

		bb.flip();

		length = bb.remaining();
		lengthBuffer = BerPacketUtils.lengthBuffer(length);
	}

	@Override
	public void write(ByteBuffer buffer) {
		BerPacketUtils.writeHeader(BerConstants.OID, lengthBuffer, buffer);
		buffer.put(bb);
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
