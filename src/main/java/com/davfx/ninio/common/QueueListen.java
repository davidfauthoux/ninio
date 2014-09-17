package com.davfx.ninio.common;

import java.io.IOException;

public final class QueueListen implements Listen {
	private final Queue queue;
	private final Listen wrappee;
	public QueueListen(Queue queue, Listen wrappee) {
		this.queue = queue;
		this.wrappee = wrappee;
	}
	
	@Override
	public void listen(final Address address, final SocketListening listening) {
		queue.post(new Runnable() {
			@Override
			public void run() {
				wrappee.listen(address, new SocketListening() {
					
					@Override
					public void failed(IOException e) {
						listening.failed(e);
					}
					
					@Override
					public void close() {
						listening.close();
					}
					
					@Override
					public CloseableByteBufferHandler connected(Address address, CloseableByteBufferHandler connection) {
						return listening.connected(address, new QueueCloseableByteBufferHandler(queue, connection));
					}
				});
			}
		});
	}
}
