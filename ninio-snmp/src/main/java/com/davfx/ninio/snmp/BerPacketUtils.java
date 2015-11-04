package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

import com.google.common.base.Charsets;

public final class BerPacketUtils {
	private static final int MAX_LENGTH_BUFFER_SIZE = 10; // 7 or 8 sufficient?

	private BerPacketUtils() {
	}
	
	public static void writeHeader(int type, ByteBuffer lengthBuffer, ByteBuffer buffer) {
		buffer.put((byte) type);
		int p = lengthBuffer.position();
		buffer.put(lengthBuffer);
		lengthBuffer.position(p);
	}
	
	public static int typeAndLengthBufferLength(ByteBuffer lengthBuffer) {
		return 1 + lengthBuffer.remaining();
	}
	
	public static ByteBuffer bytes(String s) {
		return ByteBuffer.wrap(s.getBytes(Charsets.US_ASCII));
	}
	public static String string(ByteBuffer bb) {
		ByteBuffer d = bb.duplicate();
		byte[] b = new byte[d.remaining()];
		d.get(b);
		return new String(b, Charsets.US_ASCII); // Note that this copy is no more useful in Java 7
	}
	
	public static ByteBuffer lengthBuffer(int length) {
		ByteBuffer buffer = ByteBuffer.allocate(MAX_LENGTH_BUFFER_SIZE);
		if (length < 0x80) {
			buffer.put((byte) length);
		} else {
			int mask = 0xFF;
			int bits = 0;
			int count = 0;

			while (true) {
				mask <<= 8;
				count++;
				if ((length & mask) == 0) {
					break;
				}
				bits += 8;
			}

			buffer.put((byte) (BerConstants.ASN_BIT8 | count)); // Number of bytes

			while (bits >= 0) {
				int b = (length >>> bits) & 0xFF;
				buffer.put((byte) b);
				bits -= 8;
			}
		}

		buffer.flip();
		return buffer;
	}


}
