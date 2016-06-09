package com.davfx.ninio.ssh.v3;

import java.nio.ByteBuffer;

import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class SshPacketBuilder {
	private static final Config CONFIG = ConfigUtils.load(SshPacketBuilder.class);
	private static final int MAX_PACKET_SIZE = CONFIG.getBytes("ninio.ssh.buffer.max").intValue();

	private ByteBuffer buffer = ByteBuffer.allocate(MAX_PACKET_SIZE);

	public SshPacketBuilder() {
		buffer.position(SshSpecification.OPTIMIZATION_SPACE);
	}

	public SshPacketBuilder writeInt(long i) {
		buffer.putInt((int) i);
		return this;
	}

	public SshPacketBuilder writeMpInt(byte[] i) {
		if ((i[0] & 0x80) != 0) {
			buffer.putInt(i.length + 1);
			buffer.put((byte) 0);
		} else {
			buffer.putInt(i.length);
		}
		buffer.put(i);
		return this;
	}

	public SshPacketBuilder writeByte(int b) {
		buffer.put((byte) b);
		return this;
	}

	public SshPacketBuilder writeBlob(byte[] b) {
		writeInt(b.length);
		buffer.put(b);
		return this;
	}

	public SshPacketBuilder append(byte[] b) {
		buffer.put(b);
		return this;
	}

	public SshPacketBuilder writeBlob(ByteBuffer b) {
		writeInt(b.remaining());
		buffer.put(b);
		return this;
	}

	public SshPacketBuilder append(ByteBuffer b) {
		buffer.put(b);
		return this;
	}

	public SshPacketBuilder writeString(String s) {
		writeBlob(s.getBytes(SshSpecification.CHARSET));
		return this;
	}

	public ByteBuffer finish() {
		buffer.flip();
		buffer.position(SshSpecification.OPTIMIZATION_SPACE);

		ByteBuffer b = buffer;
		buffer = null;
		return b;
	}

}
