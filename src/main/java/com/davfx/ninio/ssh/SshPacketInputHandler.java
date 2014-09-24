package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.google.common.primitives.Ints;

final class SshPacketInputHandler implements CloseableByteBufferHandler {
	private ByteBuffer buffer = null;
	private ByteBuffer paddingBuffer = null;
	private ByteBuffer lengthBuffer = ByteBuffer.wrap(new byte[Ints.BYTES]);
	private int state = 0;
	private int length = -1;
	private final CloseableByteBufferHandler wrappee;
	
	public SshPacketInputHandler(CloseableByteBufferHandler wrappee) {
		this.wrappee = wrappee;
	}
	
	@Override
	public void close() {
		wrappee.close();
	}
	
	@Override
	public void handle(Address address, ByteBuffer b) {
		while (b.hasRemaining()) {
			if (state == 0) {
				ByteBufferUtils.transfer(b, lengthBuffer);
				if (lengthBuffer.position() == lengthBuffer.capacity()) {
					lengthBuffer.flip();
					length = lengthBuffer.getInt();
					lengthBuffer.rewind();
					state = 1;
				}
			}
			
			if (state == 1) {
				int padding = b.get() & 0xFF;
				buffer = ByteBuffer.wrap(new byte[length - 1 - padding]);
				paddingBuffer = ByteBuffer.wrap(new byte[padding]);
				length = -1;
				state = 2;
			}
			
			if (state == 2) {
				ByteBufferUtils.transfer(b, buffer);
				if (buffer.position() == buffer.capacity()) {
					buffer.flip();
					ByteBuffer bb = buffer;
					buffer = null;
					state = 3;
					wrappee.handle(address, bb);
				}
			}
			if (state == 3) {
				ByteBufferUtils.transfer(b, paddingBuffer);
				if (paddingBuffer.position() == paddingBuffer.capacity()) {
					paddingBuffer = null;
					state = 0;
				}
			}
		}
	}
}
