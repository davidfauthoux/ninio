package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.security.SecureRandom;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;

final class SshPacketOuputHandler implements CloseableByteBufferHandler {
	static final int PLEASE_AVOID_COPY_OFFSET = Integer.BYTES + Byte.BYTES;
	
	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int PADDING = 16;
	
	private final CloseableByteBufferHandler wrappee;
	
	public SshPacketOuputHandler(CloseableByteBufferHandler wrappee) {
		this.wrappee = wrappee;
	}
	
	@Override
	public void close() {
		wrappee.close();
	}
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		int len = buffer.remaining();
		
		int pad = (-(len + Integer.BYTES + Byte.BYTES)) & (PADDING - 1);
		if (pad < PADDING) {
			pad += PADDING;
		}

		byte[] r = new byte[pad];
		RANDOM.nextBytes(r);

		if (buffer.position() < PLEASE_AVOID_COPY_OFFSET) {
			ByteBuffer b = ByteBuffer.allocate(Integer.BYTES + Byte.BYTES + len + pad);
			b.putInt(len + pad + Byte.BYTES);
			b.put((byte) pad);
			b.put(buffer);
			b.put(r);
			b.flip();
			buffer = b;
		} else {
			// This optimisation is valid (no buffer copy) if ByteBuffer has space before the actual data
			buffer.position(buffer.position() - PLEASE_AVOID_COPY_OFFSET);
			buffer.putInt(len + pad + Byte.BYTES);
			buffer.put((byte) pad);
			buffer.position(buffer.position() + len);
			buffer.limit(buffer.position() + r.length);
			buffer.put(r);
			buffer.position(buffer.position() - r.length - len - PLEASE_AVOID_COPY_OFFSET);
		}
		
		wrappee.handle(address, buffer);
	}
}
