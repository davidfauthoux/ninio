package com.davfx.ninio.core;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class ToFileReceiverClosing implements Receiver, Closing, Closeable, AutoCloseable {
	
	private final OutputStream out;
	private final Closing closing;
	private final Failing failing;
	
	public ToFileReceiverClosing(File file, Closing closing, Failing failing) throws IOException {
		this.closing = closing;
		this.failing = failing;
		out = new FileOutputStream(file);
	}
	
	@Override
	public void received(Address address, ByteBuffer buffer) {
		try {
			out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
		} catch (IOException e) {
			failing.failed(e);
		}
		buffer.position(buffer.position() + buffer.remaining());
	}
	
	@Override
	public void close() throws IOException {
		out.close();
	}
	
	@Override
	public void closed() {
		try {
			out.close();
			closing.closed();
		} catch (IOException e) {
			failing.failed(e);
		}
	}
}
