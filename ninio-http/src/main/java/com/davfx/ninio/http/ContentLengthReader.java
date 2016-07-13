package com.davfx.ninio.http;

import java.io.IOException;
import java.nio.ByteBuffer;

final class ContentLengthReader implements HttpContentReceiver {

	private final ReaderFailing failing;
	private final HttpContentReceiver wrappee;
	
	private final long contentLength;
	private long countRead = 0L;

	private boolean ended = false;
	
	public ContentLengthReader(long contentLength, ReaderFailing failing, HttpContentReceiver wrappee) {
		this.failing = failing;
		this.wrappee = wrappee;
		this.contentLength = contentLength;
	}
	
	// MUST sync-ly consume buffer
	@Override
	public void received(ByteBuffer buffer) {
		if (ended) {
			throw new IllegalStateException();
		}
		
		if ((countRead + buffer.remaining()) > contentLength) {
			ByteBuffer b = buffer.duplicate();
			int l = (int) (contentLength - countRead);
			b.limit(b.position() + l);
			buffer.position(buffer.position() + l);
			buffer = b;
		}
		
		countRead += buffer.remaining();
		if (buffer.hasRemaining()) {
			wrappee.received(buffer);
		}
		if (countRead == contentLength) {
			ended = true;
			wrappee.ended();
		}
	}
	
	@Override
	public void ended() {
		if (ended) {
			throw new IllegalStateException();
		}
		ended = true;
		failing.failed(new IOException("Connection closed prematurely"));
	}
}
