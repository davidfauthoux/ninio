package com.davfx.ninio.snmp;

import java.nio.ByteBuffer;

public final class Version3PacketBuilder {
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

	private Version3PacketBuilder(AuthRemoteEngine authEngine, int requestId, Oid oid, int type, int bulkLength) {
		authEngine.renewTime();
		
		boolean encrypt = false;
		int securityFlags = 0x0;
		if (authEngine.isReady()) {
			if (authEngine.getAuthLogin() != null) {
				securityFlags |= BerConstants.VERSION_3_AUTH_FLAG;
				if (authEngine.getPrivLogin() != null) {
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
				.add(new BytesBerPacket(ByteBuffer.wrap(authEngine.getId())))
				.add(new IntegerBerPacket(authEngine.getBootCount()))
				.add(new IntegerBerPacket(authEngine.getTime()))
				.add(new BytesBerPacket(BerPacketUtils.bytes(authEngine.getAuthLogin())))
				.add(auth)
				.add(priv)));

		BerPacket pduPacket = new SequenceBerPacket(BerConstants.SEQUENCE)
			.add(new BytesBerPacket(ByteBuffer.wrap(authEngine.getId())))
			.add(new BytesBerPacket(ByteBuffer.allocate(0)))
			.add(new SequenceBerPacket(type)
				.add(new IntegerBerPacket(requestId))
				.add(new IntegerBerPacket(0))
				.add(new IntegerBerPacket(bulkLength))
				.add(new SequenceBerPacket(BerConstants.SEQUENCE)
					.add(new SequenceBerPacket(BerConstants.SEQUENCE)
						.add(new OidBerPacket(oid))
						.add(new NullBerPacket()))));

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

	public static Version3PacketBuilder getBulk(AuthRemoteEngine authEngine, int requestId, Oid oid, int bulkLength) {
		return new Version3PacketBuilder(authEngine, requestId, oid, BerConstants.GETBULK, bulkLength);
	}
	public static Version3PacketBuilder get(AuthRemoteEngine authEngine, int requestId, Oid oid) {
		return new Version3PacketBuilder(authEngine, requestId, oid, BerConstants.GET, 0);
	}
	public static Version3PacketBuilder getNext(AuthRemoteEngine authEngine, int requestId, Oid oid) {
		return new Version3PacketBuilder(authEngine, requestId, oid, BerConstants.GETNEXT, 0);
	}

	public ByteBuffer getBuffer() {
		return buffer;
	}
}
