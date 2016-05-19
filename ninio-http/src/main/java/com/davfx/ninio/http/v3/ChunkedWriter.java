package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

final class ChunkedWriter implements HttpContentSender {
	private final HttpContentSender wrappee;

	private final ByteBuffer emptyLineByteBuffer = LineReader.toBuffer("");
	private final ByteBuffer zeroByteBuffer = LineReader.toBuffer(Integer.toHexString(0));

	private boolean finished = false;
	
	public ChunkedWriter(HttpContentSender wrappee) {
		this.wrappee = wrappee;
	}

	@Override
	public HttpContentSender send(ByteBuffer buffer) {
		if (finished) {
			throw new IllegalStateException();
		}
		if (!buffer.hasRemaining()) {
			return this;
		}
		wrappee.send(LineReader.toBuffer(Integer.toHexString(buffer.remaining())));
		wrappee.send(buffer);
		wrappee.send(emptyLineByteBuffer.duplicate());
		return this;
	}

	@Override
	public void finish() {
		if (finished) {
			throw new IllegalStateException();
		}
		finished = true;
		wrappee.send(zeroByteBuffer.duplicate());
		wrappee.send(emptyLineByteBuffer.duplicate());
		wrappee.finish();
	}
	
	@Override
	public void cancel() {
		finished = true;
		wrappee.cancel();
	}
}
