package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;

final class SshPacketConnector implements Connecter.Connecting {
	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int PADDING = 16;
	
	private final Connecter.Connecting wrappee;
	
	public SshPacketConnector(Connecter.Connecting wrappee) {
		this.wrappee = wrappee;
	}
	
	@Override
	public void close() {
		wrappee.close();
	}
	
	@Override
	public void send(Address address, ByteBuffer buffer, Callback callback) {
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
		
		wrappee.send(address, buffer, callback);
	}
}
