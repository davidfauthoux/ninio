package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.CloseableByteBufferHandler;

final class ZlibUncompressingCloseableByteBufferHandler implements CloseableByteBufferHandler {
	private static final int BUFFER_SIZE = 4 * 1024;

	private final Inflater inflater = new Inflater();
	private boolean activated = false;
	private final CloseableByteBufferHandler wrappee;

	public ZlibUncompressingCloseableByteBufferHandler(CloseableByteBufferHandler handler) {
		this.wrappee = handler;
	}

	public void init() {
		activated = true;
	}

	@Override
	public void close() {
		wrappee.close();
	}

	@Override
	public void handle(Address address, ByteBuffer deflated) {
		if (!activated) {
			wrappee.handle(address, deflated);
			return;
		}

		int r = deflated.remaining();

		if (r > 0) {
			inflater.setInput(deflated.array(), deflated.position(), r);
			deflated.position(deflated.position() + r);

			while (true) { // !inflater.needsInput() && !inflater.finished()) {
				ByteBuffer inflated = ByteBuffer.allocate(BUFFER_SIZE);
				try {
					int c = inflater.inflate(inflated.array(), inflated.position(), inflated.remaining());
					if (c == 0) {
						break;
					}
					inflated.position(inflated.position() + c);
				} catch (DataFormatException e) {
					throw new RuntimeException("Could not inflate", e);
				}
				inflated.flip();
				wrappee.handle(null, inflated);
			}
		}
	}
}
