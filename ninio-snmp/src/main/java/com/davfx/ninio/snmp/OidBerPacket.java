package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

public final class OidBerPacket implements BerPacket {
	private final ByteBuffer lengthBuffer;
	private final int length;
	private final ByteBuffer bb;

	public OidBerPacket(Oid oid) {
		long[] raw = oid.raw;

		if (raw.length < 2) {
			throw new IllegalArgumentException();
		}

		int l = 0;
		l++;
		for (int i = 2; i < raw.length; i++) {
			long value = raw[i];
			long mask = ~0x80000000L;
			l++;
			while (true) {
				mask <<= 7;
				if ((value & mask) == 0L) {
					break;
				}
				l++;
			}
		}

		bb = ByteBuffer.allocate(l);
		bb.put((byte) ((raw[1] + (raw[0] * 40)) & 0xFF));

		for (int i = 2; i < raw.length; i++) {
			long value = raw[i];

			long mask = ~0x80000000L;
			int bits = 0;

			while (true) {
				mask <<= 7;
				if ((value & mask) == 0L) {
					break;
				}
				bits += 7;
			}

			while (bits >= 0) {
				long b = (value >>> bits) & ~0x80L;
				if (bits > 0) {
					b |= 0x80L; // Continuation bit
				}
				bb.put((byte) b);
				bits -= 7;
			}
		}

		bb.flip();

		length = l;
		lengthBuffer = BerPacketUtils.lengthBuffer(length);
	}

	@Override
	public void write(ByteBuffer buffer) {
		BerPacketUtils.writeHeader(BerConstants.OID, lengthBuffer, buffer);
		int p = bb.position();
		buffer.put(bb);
		bb.position(p);
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
