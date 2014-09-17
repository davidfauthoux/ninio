package com.davfx.ninio.common;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class QueueReady implements Ready {
	private final Queue queue;
	private final Ready wrappee;
	public QueueReady(Queue queue, Ready wrappee) {
		this.queue = queue;
		this.wrappee = wrappee;
	}
	
	@Override
	public void connect(final Address address, final ReadyConnection connection) {
		queue.post(new Runnable() {
			@Override
			public void run() {
				wrappee.connect(address, new ReadyConnection() {
					@Override
					public void failed(IOException e) {
						connection.failed(e);
					}
					@Override
					public void close() {
						connection.close();
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						connection.handle(address, buffer);
					}
					
					@Override
					public void connected(FailableCloseableByteBufferHandler write) {
						connection.connected(new QueueCloseableByteBufferHandler(queue, write));
					}
				});
			}
		});
	}
}
