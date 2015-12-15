package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

public final class CountingCurrentOpenReady implements Ready {
	
	private final Ready wrappee;
	private final Count openCount;
	
	public CountingCurrentOpenReady(Count openCount, Ready wrappee) {
		this.openCount = openCount;
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
				if (openCount != null) {
					openCount.inc(-1);
				}
				connection.close();
			}
			
			@Override
			public void handle(Address address, ByteBuffer buffer) {
				connection.handle(address, buffer);
			}
			
			@Override
			public void connected(final FailableCloseableByteBufferHandler write) {
				if (openCount != null) {
					openCount.inc(1);
				}
				connection.connected(new FailableCloseableByteBufferHandler() {
					@Override
					public void failed(IOException e) {
						if (openCount != null) {
							openCount.inc(-1);
						}
						write.failed(e);
					}
					@Override
					public void close() {
						if (openCount != null) {
							openCount.inc(-1);
						}
						write.close();
					}
					
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						write.handle(address, buffer);
					}
				});
			}
		});
	}
}
