package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

final class Version2cPacketBuilder {
	private final ByteBuffer buffer;

	private Version2cPacketBuilder(String community, int requestId, Oid oid, int type, int bulkLength) {
		SequenceBerPacket root = new SequenceBerPacket(BerConstants.SEQUENCE)
			.add(new IntegerBerPacket(BerConstants.VERSION_2C))
			.add(new BytesBerPacket(BerPacketUtils.bytes(community)))
			.add(new SequenceBerPacket(type)
				.add(new IntegerBerPacket(requestId))
				.add(new IntegerBerPacket(0))
				.add(new IntegerBerPacket(bulkLength))
				.add(new SequenceBerPacket(BerConstants.SEQUENCE)
					.add(new SequenceBerPacket(BerConstants.SEQUENCE)
						.add(new OidBerPacket(oid))
						.add(new NullBerPacket()))));

		buffer = ByteBuffer.allocate(BerPacketUtils.typeAndLengthBufferLength(root.lengthBuffer()) + root.length());
		root.write(buffer);
		buffer.flip();
	}

	public static Version2cPacketBuilder getBulk(String community, int requestId, Oid oid, int bulkLength) {
		return new Version2cPacketBuilder(community, requestId, oid, BerConstants.GETBULK, bulkLength);
	}
	public static Version2cPacketBuilder get(String community, int requestId, Oid oid) {
		return new Version2cPacketBuilder(community, requestId, oid, BerConstants.GET, 0);
	}
	public static Version2cPacketBuilder getNext(String community, int requestId, Oid oid) {
		return new Version2cPacketBuilder(community, requestId, oid, BerConstants.GETNEXT, 0);
	}

	public ByteBuffer getBuffer() {
		return buffer;
	}
}
