package com.davfx.ninio.common;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class QueueCloseableByteBufferHandler implements FailableCloseableByteBufferHandler {
	private final Queue queue;
	private final CloseableByteBufferHandler wrappee;
	public QueueCloseableByteBufferHandler(Queue queue, CloseableByteBufferHandler wrappee) {
		this.queue = queue;
		this.wrappee = wrappee;
	}
	
	@Override
	public void handle(final Address address, final ByteBuffer buffer) {
		queue.post(new Runnable() {
			@Override
			public void run() {
				wrappee.handle(address, buffer);
			}
		});
	}
	
	@Override
	public void close() {
		queue.post(new Runnable() {
			@Override
			public void run() {
				wrappee.close();
			}
		});
	}

	@Override
	public void failed(IOException e) {
		close();
	}
}
