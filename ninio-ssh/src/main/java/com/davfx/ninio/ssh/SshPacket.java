package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;

import com.google.common.base.Charsets;

public final class SshPacket {
	
	private final ByteBuffer buffer;
	
	public SshPacket(ByteBuffer buffer) {
		this.buffer = buffer;
	}
	
	public long readInt() {
		return buffer.getInt() & 0xFFFFFFFFL;
	}
	public int readShort() {
		return buffer.getShort() & 0xFFFF;
	}
	public byte[] readMpInt() {
		return readBlob();
	}
	public int readByte() {
		return buffer.get() & 0xFF;
	}
	public byte[] readBlob() {
		byte[] b = new byte[(int) readInt()];
		buffer.get(b);
		return b;
	}
	public String readString() {
		return new String(readBlob(), Charsets.UTF_8);
	}
}
