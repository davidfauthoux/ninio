package com.davfx.ninio.core;

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
					public void failed(final IOException e) {
						queue.post(new Runnable() {
							@Override
							public void run() {
								connection.failed(e);
							}
						});
					}
					@Override
					public void close() {
						queue.post(new Runnable() {
							@Override
							public void run() {
								connection.close();
							}
						});
					}
					
					@Override
					public void handle(final Address address, final ByteBuffer buffer) {
						queue.post(new Runnable() {
							@Override
							public void run() {
								connection.handle(address, buffer);
							}
						});
					}
					
					@Override
					public void connected(final FailableCloseableByteBufferHandler write) {
						queue.post(new Runnable() {
							@Override
							public void run() {
								connection.connected(new QueueCloseableByteBufferHandler(queue, write));
							}
						});
					}
				});
			}
		});
	}
}
