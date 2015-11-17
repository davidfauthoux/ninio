package com.davfx.ninio.telnet;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;
import com.davfx.ninio.core.ReadyConnection;

public final class CutOnPromptReadyConnection implements ReadyConnection {
	private final ReadyConnection wrappee;
	private CuttingByteBufferHandler write;
	
	private final int limit;

	public CutOnPromptReadyConnection(int limit, ReadyConnection wrappee) {
		this.limit = limit;
		this.wrappee = wrappee;
	}
	
	public void setPrompt(ByteBuffer prompt) {
		write.setPrompt(prompt);
	}
	
	@Override
	public void connected(FailableCloseableByteBufferHandler write) {
		this.write = new CuttingByteBufferHandler(limit, write);
		wrappee.connected(write);
	}
	@Override
	public void close() {
		write = null;
		wrappee.close();
	}
	@Override
	public void failed(IOException e) {
		write = null;
		wrappee.failed(e);
	}
	
	@Override
	public void handle(Address address, ByteBuffer buffer) {
		write.handle(address, buffer);
	}
}
