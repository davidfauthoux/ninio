package com.davfx.ninio.core;

import java.io.IOException;

public final class QueueListen implements Listen {
	private final Queue queue;
	private final Listen wrappee;
	public QueueListen(Queue queue, Listen wrappee) {
		this.queue = queue;
		this.wrappee = wrappee;
	}
	
	@Override
	public void listen(final Address address, final SocketListening socketListening) {
		queue.post(new Runnable() {
			@Override
			public void run() {
				wrappee.listen(address, new SocketListening() {
					
					@Override
					public void failed(IOException e) {
						socketListening.failed(e);
					}
					
					@Override
					public void close() {
						socketListening.close();
					}
					
					@Override
					public void listening(final Listening listening) {
						socketListening.listening(new Listening() {
							@Override
							public void disconnect() {
								queue.post(new Runnable() {
									@Override
									public void run() {
										listening.disconnect();
									}
								});
							}
							@Override
							public void close() {
								queue.post(new Runnable() {
									@Override
									public void run() {
										listening.close();
									}
								});
							}
						});
					}
					
					@Override
					public CloseableByteBufferHandler connected(Address address, CloseableByteBufferHandler connection) {
						return socketListening.connected(address, new QueueCloseableByteBufferHandler(queue, connection));
					}
				});
			}
		});
	}
}
