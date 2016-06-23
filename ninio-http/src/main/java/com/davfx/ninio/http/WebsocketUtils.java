package com.davfx.ninio.http;

import java.nio.ByteBuffer;

public final class WebsocketUtils {

	private WebsocketUtils() {
	}

	public static ByteBuffer headerOf(int opcode, long len) { // No mask
		int extendedPayloadLengthLen;
		if (len <= 125) {
			extendedPayloadLengthLen = 0;
		} else if (len <= 65535) {
			extendedPayloadLengthLen = 2;
		} else {
			extendedPayloadLengthLen = 8;
		}
		ByteBuffer res = ByteBuffer.allocate(2 + extendedPayloadLengthLen);
		byte first = (byte) opcode;
		first |= 0x80; // Not an ACK
		res.put(first);
		if (len <= 125) {
			res.put((byte) len);
		} else if (len <= 65535) {
			res.put((byte) 126);
			res.putShort((short) len);
		} else {
			res.put((byte) 127);
			res.putLong(len);
		}
		res.flip();
		return res;
	}
}
