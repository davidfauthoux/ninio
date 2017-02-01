package com.davfx.ninio.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connection;
import com.google.common.primitives.Ints;

final class SshPacketConnection implements Connection {
	
	private ByteBuffer buffer = null;
	private ByteBuffer paddingBuffer = null;
	private ByteBuffer lengthBuffer = ByteBuffer.allocate(Ints.BYTES);
	private int state = 0;
	private int length = -1;
	private final Connection wrappee;
	
	public SshPacketConnection(Connection wrappee) {
		this.wrappee = wrappee;
	}

	@Override
	public void received(Address address, ByteBuffer b) {
		while (b.hasRemaining()) {
			if (state == 0) {
				ByteBufferUtils.transfer(b, lengthBuffer);
				if (lengthBuffer.position() == lengthBuffer.capacity()) {
					lengthBuffer.flip();
					length = lengthBuffer.getInt();
					lengthBuffer.clear();
					state = 1;
				}
			}
			
			if (state == 1) {
				if (b.hasRemaining()) {
					int padding = b.get() & 0xFF;
					buffer = ByteBuffer.allocate(length - 1 - padding);
					paddingBuffer = ByteBuffer.allocate(padding);
					length = -1;
					state = 2;
				}
			}
			
			if (state == 2) {
				ByteBufferUtils.transfer(b, buffer);
				if (buffer.position() == buffer.capacity()) {
					buffer.flip();
					state = 3;
				}
			}
			if (state == 3) {
				ByteBufferUtils.transfer(b, paddingBuffer);
				if (paddingBuffer.position() == paddingBuffer.capacity()) {
					paddingBuffer = null;
					state = 0;

					ByteBuffer bb = buffer;
					buffer = null;
					wrappee.received(address, bb);
				}
			}
		}
	}
	
	@Override
	public void closed() {
		wrappee.closed();
	}
	
	@Override
	public void failed(IOException ioe) {
		wrappee.failed(ioe);
	}
	
	@Override
	public void connected(Address address) {
		wrappee.connected(address);
	}
}
