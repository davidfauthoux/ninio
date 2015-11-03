package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class CountingReady implements Ready {
	private final Ready wrappee;
	private final Count readCount;
	private final Count writeCount;
	
	public CountingReady(Count readCount, Count writeCount, Ready wrappee) {
		this.readCount = readCount;
		this.writeCount = writeCount;
		this.wrappee = wrappee;
	}
	
	@Override
	public void connect(Address address, final ReadyConnection connection) {
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
				if (readCount != null) {
					readCount.inc(buffer.remaining());
				}
				connection.handle(address, buffer);
			}
			
			@Override
			public void connected(final FailableCloseableByteBufferHandler write) {
				connection.connected(new FailableCloseableByteBufferHandler() {
					@Override
					public void failed(IOException e) {
						write.failed(e);
					}
					@Override
					public void close() {
						write.close();
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						if (writeCount != null) {
							writeCount.inc(buffer.remaining());
						}
						write.handle(address, buffer);
					}
				});
			}
		});
	}
}
