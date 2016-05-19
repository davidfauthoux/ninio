package com.davfx.ninio.http.v3;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.v3.Failing;

final class ContentLengthReader implements HttpContentReceiver, Failing {

	private final HttpContentReceiver wrappee;
	private final Failing failing;
	
	private final long contentLength;
	private long countRead = 0L;

	private boolean ended = false;
	
	public ContentLengthReader(long contentLength, Failing failing, HttpContentReceiver wrappee) {
		this.failing = failing;
		this.wrappee = wrappee;
		this.contentLength = contentLength;
	}
	
	@Override
	public void received(ByteBuffer buffer) {
		if (ended) {
			throw new IllegalStateException();
		}
		if (!buffer.hasRemaining()) {
			return;
		}
		
		if ((countRead + buffer.remaining()) > contentLength) {
			ByteBuffer b = buffer.duplicate();
			b.limit((int) (b.position() + (contentLength - countRead)));
			buffer = b;
		}
		
		countRead += buffer.remaining();
		wrappee.received(buffer);
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
	
	@Override
	public void failed(IOException e) {
		if (ended) {
			throw new IllegalStateException();
		}
		ended = true;
		failing.failed(e);
	}
}
