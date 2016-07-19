package com.davfx.ninio.http;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Nop;
import com.davfx.ninio.core.SendCallback;

final class ChunkedWriter implements HttpContentSender {
	private final HttpContentSender wrappee;

	private final ByteBuffer emptyLineByteBuffer = LineReader.toBuffer("");
	private final ByteBuffer zeroByteBuffer = LineReader.toBuffer(Integer.toHexString(0));

	private boolean finished = false;
	
	public ChunkedWriter(HttpContentSender wrappee) {
		this.wrappee = wrappee;
	}

	@Override
	public HttpContentSender send(final ByteBuffer buffer, final SendCallback callback) {
		if (finished) {
			throw new IllegalStateException();
		}
		if (!buffer.hasRemaining()) {
			callback.sent();
			return this;
		}
		
		wrappee.send(LineReader.toBuffer(Integer.toHexString(buffer.remaining())), new Nop());
		wrappee.send(buffer, new Nop());
		wrappee.send(emptyLineByteBuffer.duplicate(), callback);
		return this;
	}

	@Override
	public void finish() {
		if (finished) {
			throw new IllegalStateException();
		}
		
		finished = true;
		
		wrappee.send(zeroByteBuffer.duplicate(), new Nop());
		wrappee.send(emptyLineByteBuffer.duplicate(), new Nop());
		wrappee.finish();
	}
	
	@Override
	public void cancel() {
		finished = true;
		wrappee.cancel();
	}
}
