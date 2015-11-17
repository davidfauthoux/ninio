package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.Ready;
import com.davfx.ninio.core.ReadyConnection;
import com.davfx.ninio.core.ReadyFactory;

public final class TelnetClient implements TelnetReady {

	private final Queue queue;
	private final ReadyFactory readyFactory;
	private final Address address;

	public TelnetClient(Queue queue, ReadyFactory readyFactory, Address address) {
		this.queue = queue;
		this.readyFactory = readyFactory;
		this.address = address;
	}
	
	@Override
	public void connect(final ReadyConnection clientHandler) {
		queue.post(new Runnable() {
			@Override
			public void run() {
				Ready ready = readyFactory.create(queue);
				ready.connect(address, new ReadyConnection() {
					private FailableCloseableByteBufferHandler write;
					private TelnetResponseReader reader;
					@Override
					public void handle(Address address, ByteBuffer buffer) {
						reader.handle(address, buffer, clientHandler, write);
					}
					
					@Override
					public void failed(IOException e) {
						clientHandler.failed(e);
					}
					
					@Override
					public void connected(final FailableCloseableByteBufferHandler write) {
						this.write = write;
						reader = new TelnetResponseReader();
						clientHandler.connected(write);
					}
					
					@Override
					public void close() {
						clientHandler.close();
					}
				});
			}
		});
	}

}
