package com.davfx.ninio.snmp.v3;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public final class SequenceBerPacket implements BerPacket {
	private final List<BerPacket> toWrite = new LinkedList<>();
	private final int type;

	public SequenceBerPacket(int type) {
		this.type = type;
	}

	@Override
	public int length() {
		int l = 0;
		for (BerPacket p : toWrite) {
			l += BerPacketUtils.typeAndLengthBufferLength(p.lengthBuffer()) + p.length();
		}
		return l;
	}

	@Override
	public ByteBuffer lengthBuffer() {
		return BerPacketUtils.lengthBuffer(length());
	}

	@Override
	public void write(ByteBuffer buffer) {
		BerPacketUtils.writeHeader(type, lengthBuffer(), buffer);
		for (BerPacket p : toWrite) {
			p.write(buffer);
		}
	}

	public SequenceBerPacket add(BerPacket sequence) {
		toWrite.add(sequence);
		return this;
	}
}
