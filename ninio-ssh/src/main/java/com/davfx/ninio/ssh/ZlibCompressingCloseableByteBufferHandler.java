package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;

final class ZlibCompressingCloseableByteBufferHandler implements CloseableByteBufferHandler {
	private static final int BUFFER_SIZE = 4 * 1024;

	private final CloseableByteBufferHandler wrappee;
	private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
	private boolean activated = false;

	public ZlibCompressingCloseableByteBufferHandler(CloseableByteBufferHandler handler) {
		this.wrappee = handler;
	}

	public void init() {
		activated = true;
	}

	@Override
	public void handle(Address address, ByteBuffer buffer) {
		if (!activated) {
			wrappee.handle(address, buffer);
			return;
		}
		deflater.setInput(buffer.array(), buffer.position(), buffer.remaining());
		buffer.position(buffer.limit());
		write(address);
	}

	@Override
	public void close() {
		wrappee.close();
	}

	private void write(Address address) {
		while (true) {
			int offset = SshPacketOuputHandler.PLEASE_AVOID_COPY_OFFSET;
			ByteBuffer deflated = ByteBuffer.allocate(BUFFER_SIZE);
			deflated.position(offset);
			int c = deflater.deflate(deflated.array(), deflated.position(), deflated.remaining(), Deflater.SYNC_FLUSH);
			if (c == 0) {
				break;
			}
			deflated.position(deflated.position() + c);
			deflated.flip();
			deflated.position(offset);
			wrappee.handle(address, deflated);
		}
	}
}
