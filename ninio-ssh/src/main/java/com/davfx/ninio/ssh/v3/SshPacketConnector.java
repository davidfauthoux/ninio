package com.davfx.ninio.ssh.v3;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Connector;

final class SshPacketConnector implements Connector {
	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int PADDING = 16;
	
	private final Connector wrappee;
	
	public SshPacketConnector(Connector wrappee) {
		this.wrappee = wrappee;
	}
	
	@Override
	public void close() {
		wrappee.close();
	}
	
	@Override
	public Connector send(Address address, ByteBuffer buffer) {
		int len = buffer.remaining();
		
		int pad = (-(len + SshSpecification.OPTIMIZATION_SPACE)) & (PADDING - 1);
		if (pad < PADDING) {
			pad += PADDING;
		}

		byte[] r = new byte[pad];
		RANDOM.nextBytes(r);

		if (buffer.position() < SshSpecification.OPTIMIZATION_SPACE) {
			ByteBuffer b = ByteBuffer.allocate(SshSpecification.OPTIMIZATION_SPACE + len + pad);
			b.putInt(len + pad + 1);
			b.put((byte) pad);
			b.put(buffer);
			b.put(r);
			b.flip();
			buffer = b;
		} else {
			// This optimisation is valid (no buffer copy) if ByteBuffer has space before the actual data
			buffer.position(buffer.position() - SshSpecification.OPTIMIZATION_SPACE);
			buffer.putInt(len + pad + 1);
			buffer.put((byte) pad);
			buffer.position(buffer.position() + len);
			buffer.limit(buffer.position() + r.length);
			buffer.put(r);
			buffer.position(buffer.position() - r.length - len - SshSpecification.OPTIMIZATION_SPACE);
		}
		
		wrappee.send(address, buffer);
		return this;
	}
}
