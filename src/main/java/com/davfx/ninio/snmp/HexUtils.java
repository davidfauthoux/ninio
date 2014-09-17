package com.davfx.ninio.snmp;

import java.math.BigInteger;
import java.nio.ByteBuffer;

final class HexUtils {
	private HexUtils() {
	}
	
	private static final String HEXES = "0123456789ABCDEF";

	public static String toHexString(ByteBuffer bb) {
		ByteBuffer buffer = bb.duplicate();
		StringBuilder hex = new StringBuilder();
		while (buffer.hasRemaining()) {
			byte b = buffer.get();
			hex
					.append(HEXES.charAt((b & 0xF0) >> 4))
					.append(HEXES.charAt((b & 0x0F)));
		}
		return hex.toString();
	}

	public static String toHexString(int[] raw) {
		StringBuilder hex = new StringBuilder();
		for (int b : raw) {
			hex
					.append(HEXES.charAt((b & 0xF000) >> 12))
					.append(HEXES.charAt((b & 0x0F00) >> 8))
					.append(HEXES.charAt((b & 0x00F0) >> 4))
					.append(HEXES.charAt((b & 0x000F)));
		}
		return hex.toString();
	}

	public static ByteBuffer fromHexString(String s) {
		return ByteBuffer.wrap(new BigInteger(s, 16).toByteArray());
	}
	
}
