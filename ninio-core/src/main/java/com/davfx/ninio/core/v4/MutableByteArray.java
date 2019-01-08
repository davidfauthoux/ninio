package com.davfx.ninio.core.v4;

public final class MutableByteArray {

	public final byte[][] bytes;

	public MutableByteArray(byte[][] bytes) {
		this.bytes = bytes;
	}

	public ByteArray toByteArray() {
		return new ByteArray(bytes);
	}
}
