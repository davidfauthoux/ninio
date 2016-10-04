package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

public final class Version2cPacketBuilder {
	
	private static interface Value {
		BerPacket ber();
	}
	
	private static final class OidValue {
		public final Oid oid;
		public final Value value;
		public OidValue(Oid oid, Value value) {
			this.oid = oid;
			this.value = value;
		}
	}
	
	private final ByteBuffer buffer;

	private Version2cPacketBuilder(String community, int requestId, int type, int bulkLength, Iterable<OidValue> oidValues) {
		SequenceBerPacket seq = new SequenceBerPacket(BerConstants.SEQUENCE);
		for (OidValue oidValue : oidValues) {
			seq.add(new SequenceBerPacket(BerConstants.SEQUENCE)
						.add(new OidBerPacket(oidValue.oid))
						.add(oidValue.value.ber()));
		}
		SequenceBerPacket root = new SequenceBerPacket(BerConstants.SEQUENCE)
			.add(new IntegerBerPacket(BerConstants.VERSION_2C))
			.add(new BytesBerPacket(BerPacketUtils.bytes(community)))
			.add(new SequenceBerPacket(type)
				.add(new IntegerBerPacket(requestId))
				.add(new IntegerBerPacket(0))
				.add(new IntegerBerPacket(bulkLength))
				.add(seq));

		buffer = ByteBuffer.allocate(BerPacketUtils.typeAndLengthBufferLength(root.lengthBuffer()) + root.length());
		root.write(buffer);
		buffer.flip();
	}

	private static Iterable<OidValue> single(Oid oid) {
		List<OidValue> l = new LinkedList<>();
		l.add(new OidValue(oid, new Value() {
			@Override
			public BerPacket ber() {
				return new NullBerPacket();
			}
		}));
		return l;
	}
	
	public static Version2cPacketBuilder getBulk(String community, int requestId, Oid oid, int bulkLength) {
		return new Version2cPacketBuilder(community, requestId, BerConstants.GETBULK, bulkLength, single(oid));
	}
	public static Version2cPacketBuilder get(String community, int requestId, Oid oid) {
		return new Version2cPacketBuilder(community, requestId, BerConstants.GET, 0, single(oid));
	}
	public static Version2cPacketBuilder getNext(String community, int requestId, Oid oid) {
		return new Version2cPacketBuilder(community, requestId, BerConstants.GETNEXT, 0, single(oid));
	}

	public static Version2cPacketBuilder trap(String community, int requestId, final Oid trapOid, Iterable<SnmpResult> oidValues) {
		List<OidValue> l = new LinkedList<>();
		l.add(new OidValue(BerConstants.TIMESTAMP_OID, new Value() {
			@Override
			public BerPacket ber() {
				return new IntegerBerPacket((int) (System.currentTimeMillis() / 10L));
			}
		}));
		l.add(new OidValue(BerConstants.TRAP_OID, new Value() {
			@Override
			public BerPacket ber() {
				return new OidBerPacket(trapOid);
			}
		}));
		for (final SnmpResult oidValue : oidValues) {
			l.add(new OidValue(oidValue.oid, new Value() {
				@Override
				public BerPacket ber() {
					return new BytesBerPacket(BerPacketUtils.bytes(oidValue.value));
				}
			}));
		}
		return new Version2cPacketBuilder(community, requestId, BerConstants.TRAP, 0, l);
	}

	public ByteBuffer getBuffer() {
		return buffer;
	}
}
