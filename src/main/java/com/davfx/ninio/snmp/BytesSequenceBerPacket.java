package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

public final class BytesSequenceBerPacket implements BerPacket {
	private final BerPacket packet;

	public BytesSequenceBerPacket(BerPacket packet) {
		this.packet = packet;
	}
	
	@Override
	public void write(ByteBuffer buffer) {
		BerPacketUtils.writeHeader(BerConstants.OCTETSTRING, lengthBuffer(), buffer);
		packet.write(buffer);
	}
	@Override
	public int length() {
			return BerPacketUtils.typeAndLengthBufferLength(packet.lengthBuffer()) + packet.length();
	}

	@Override
	public ByteBuffer lengthBuffer() {
		return BerPacketUtils.lengthBuffer(length());
	}
}