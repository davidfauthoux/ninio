package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

public final class Version3PacketBuilder {
/*
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
*/

	private final ByteBuffer buffer;

	private static final class AuthBerPacket implements BerPacket {
		private static final int LENGTH = 12;
		private final ByteBuffer lengthBuffer = BerPacketUtils.lengthBuffer(LENGTH);
		private int position = -1;
		public AuthBerPacket() {
		}
		
		@Override
		public void write(ByteBuffer buffer) {
			BerPacketUtils.writeHeader(BerConstants.OCTETSTRING, lengthBuffer, buffer);
			position = buffer.position();
			buffer.put(new byte[LENGTH]);
		}
		@Override
		public ByteBuffer lengthBuffer() {
			return lengthBuffer;
		}
		@Override
		public int length() {
			return LENGTH;
		}
	}
	
	private static final class PrivacyBerPacket implements BerPacket {
		private static final int LENGTH = 8;
		private final ByteBuffer lengthBuffer = BerPacketUtils.lengthBuffer(LENGTH);
		private int position = -1;
		public PrivacyBerPacket() {
		}
		
		@Override
		public void write(ByteBuffer buffer) {
			BerPacketUtils.writeHeader(BerConstants.OCTETSTRING, lengthBuffer, buffer);
			position = buffer.position();
			buffer.put(new byte[LENGTH]);
		}
		@Override
		public ByteBuffer lengthBuffer() {
			return lengthBuffer;
		}
		@Override
		public int length() {
			return LENGTH;
		}
	}

	private Version3PacketBuilder(AuthRemoteEngine authEngine, int requestId, int type, int bulkLength, Oid oid) {
		authEngine.renewTime();
		
		boolean encrypt = false;
		int securityFlags = 0x0;
		if (authEngine.getId() != null) {
			if (authEngine.getAuthLogin() != null) {
				securityFlags |= BerConstants.VERSION_3_AUTH_FLAG;
				if (authEngine.usePriv()) {
					securityFlags |= BerConstants.VERSION_3_PRIV_FLAG;
					encrypt = true;
				}
			}
		}
		securityFlags |= BerConstants.VERSION_3_REPORTABLE_FLAG;
	
		SequenceBerPacket root = new SequenceBerPacket(BerConstants.SEQUENCE)
			.add(new IntegerBerPacket(BerConstants.VERSION_3))
			.add(new SequenceBerPacket(BerConstants.SEQUENCE)
				.add(new IntegerBerPacket(authEngine.incPacketNumber())) // Packet number
				.add(new IntegerBerPacket(65535)) // Max packet size
				.add(new BytesBerPacket(ByteBuffer.wrap(new byte[] { (byte) securityFlags })))
				.add(new IntegerBerPacket(BerConstants.VERSION_3_USM_SECURITY_MODEL)));

		AuthBerPacket auth = new AuthBerPacket();
		PrivacyBerPacket priv = new PrivacyBerPacket();
		
		root.add(new BytesSequenceBerPacket(new SequenceBerPacket(BerConstants.SEQUENCE)
				.add(new BytesBerPacket((authEngine.getId() == null) ? ByteBuffer.allocate(0) : ByteBuffer.wrap(authEngine.getId())))
				.add(new IntegerBerPacket((authEngine.getBootCount() == 0) ? 1 : authEngine.getBootCount()))
				.add(new IntegerBerPacket((authEngine.getTime() == 0) ? 1 : authEngine.getTime()))
				.add(new BytesBerPacket(BerPacketUtils.bytes(authEngine.getAuthLogin())))
				.add(auth)
				.add(priv)
			));

		SequenceBerPacket seq = new SequenceBerPacket(BerConstants.SEQUENCE);
		seq.add(new OidBerPacket(oid)).add(new NullBerPacket());

		BerPacket pduPacket = new SequenceBerPacket(BerConstants.SEQUENCE)
			.add(new BytesBerPacket((authEngine.getId() != null) ? ByteBuffer.wrap(authEngine.getId()) : ByteBuffer.allocate(0)))
			.add(new BytesBerPacket((authEngine.authRemoteSpecification.contextName != null) ? BerPacketUtils.bytes(authEngine.authRemoteSpecification.contextName) : ByteBuffer.allocate(0)))
			.add(new SequenceBerPacket(type)
				.add(new IntegerBerPacket(requestId))
				.add(new IntegerBerPacket(0))
				.add(new IntegerBerPacket(bulkLength))
				.add(new SequenceBerPacket(BerConstants.SEQUENCE)
					.add(seq)));

		if (encrypt) {
			ByteBuffer decryptedBuffer = ByteBuffer.allocate(BerPacketUtils.typeAndLengthBufferLength(pduPacket.lengthBuffer()) + pduPacket.length());
			pduPacket.write(decryptedBuffer);
			decryptedBuffer.flip();
			root.add(new BytesBerPacket(authEngine.encrypt(decryptedBuffer)));
		} else {
			root.add(pduPacket);
		}
		
		buffer = ByteBuffer.allocate(BerPacketUtils.typeAndLengthBufferLength(root.lengthBuffer()) + root.length());
		root.write(buffer);
		buffer.flip();
		
		if (encrypt) {
			writeInside(buffer, priv.position, authEngine.getEncryptionParameters());
		}

		writeInside(buffer, auth.position, authEngine.hash(buffer));
	}
	
	private static void writeInside(ByteBuffer buffer, int position, byte[] bytes) {
		int p = buffer.position();
		buffer.position(position);
		buffer.put(bytes);
		buffer.position(p);
	}

/*
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
*/
	
	public static Version3PacketBuilder getBulk(AuthRemoteEngine authEngine, int requestId, Oid oid, int bulkLength) {
		return new Version3PacketBuilder(authEngine, requestId, BerConstants.GETBULK, bulkLength, oid);
	}
	public static Version3PacketBuilder get(AuthRemoteEngine authEngine, int requestId, Oid oid) {
		return new Version3PacketBuilder(authEngine, requestId, BerConstants.GET, 0, oid);
	}
	public static Version3PacketBuilder getNext(AuthRemoteEngine authEngine, int requestId, Oid oid) {
		return new Version3PacketBuilder(authEngine, requestId, BerConstants.GETNEXT, 0, oid);
	}
	
/*
	public static Version3PacketBuilder trap(AuthRemoteEngine authEngine, int requestId, final Oid trapOid, Iterable<SnmpResult> oidValues) {
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
		return new Version3PacketBuilder(authEngine, requestId, BerConstants.TRAP, 0, l);
	}
*/
	
	public ByteBuffer getBuffer() {
		return buffer;
	}
}
