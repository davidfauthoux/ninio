package com.davfx.ninio.http;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.SendCallback;

final class ContentLengthWriter implements HttpContentSender {
	private final HttpContentSender wrappee;

	private final long contentLength;
	private long countWrite = 0L;

	private boolean finished = false;
	
	public ContentLengthWriter(long contentLength, HttpContentSender wrappee) {
		this.contentLength = contentLength;
		this.wrappee = wrappee;
	}

	@Override
	public void send(ByteBuffer buffer, SendCallback callback) {
		if (finished) {
			throw new IllegalStateException();
		}
		
		if ((countWrite + buffer.remaining()) > contentLength) {
			ByteBuffer b = buffer.duplicate();
			b.limit((int) (b.position() + (contentLength - countWrite)));
			buffer = b;
		}

		wrappee.send(buffer, callback);
	}

	@Override
	public void finish() {
		if (finished) {
			throw new IllegalStateException();
		}

		finished = true;
		wrappee.finish();
	}
	
	@Override
	public void cancel() {
		finished = true;
		wrappee.cancel();
	}
}
