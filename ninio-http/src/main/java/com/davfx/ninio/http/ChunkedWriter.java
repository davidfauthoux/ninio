package com.davfx.ninio.http;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.NopConnecterConnectingCallback;

final class ChunkedWriter implements HttpContentSender {
	private final HttpContentSender wrappee;

	private final ByteBuffer emptyLineByteBuffer = LineReader.toBuffer("");
	private final ByteBuffer zeroByteBuffer = LineReader.toBuffer(Integer.toHexString(0));

	private boolean finished = false;
	
	public ChunkedWriter(HttpContentSender wrappee) {
		this.wrappee = wrappee;
	}

	@Override
	public void send(final ByteBuffer buffer, final Connecter.SendCallback callback) {
		if (finished) {
			throw new IllegalStateException();
		}
		if (!buffer.hasRemaining()) {
			callback.sent();
			return;
		}
		
		wrappee.send(LineReader.toBuffer(Integer.toHexString(buffer.remaining())), new NopConnecterConnectingCallback());
		wrappee.send(buffer, new NopConnecterConnectingCallback());
		wrappee.send(emptyLineByteBuffer.duplicate(), callback);
	}

	@Override
	public void finish() {
		if (finished) {
			throw new IllegalStateException();
		}
		
		finished = true;
		
		wrappee.send(zeroByteBuffer.duplicate(), new NopConnecterConnectingCallback());
		wrappee.send(emptyLineByteBuffer.duplicate(), new NopConnecterConnectingCallback());
		wrappee.finish();
	}
	
	@Override
	public void cancel() {
		finished = true;
		wrappee.cancel();
	}
}
