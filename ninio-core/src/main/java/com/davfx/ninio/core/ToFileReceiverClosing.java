package com.davfx.ninio.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public final class ToFileReceiverClosing implements Connection, Disconnectable {
	
	private final OutputStream out;
	private final Connection wrappee;
	private boolean closed = false;
	
	public ToFileReceiverClosing(File file, Connection wrappee) throws IOException {
		this.wrappee = wrappee;
		out = new FileOutputStream(file);
	}
	
	@Override
	public void failed(IOException e) {
		if (closed) {
			return;
		}
		
		closed = true;
		try {
			out.close();
		} catch (IOException ce) {
		}
		wrappee.failed(e);
	}
	
	@Override
	public void received(Address address, ByteBuffer buffer) {
		if (closed) {
			return;
		}
		
		try {
			out.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining());
			wrappee.received(address, buffer);
		} catch (IOException e) {
			try {
				out.close();
			} catch (IOException ce) {
			}
			closed = true;
			wrappee.failed(e);
		}
		buffer.position(buffer.position() + buffer.remaining());
	}
	
	@Override
	public void connected(Address address) {
		if (closed) {
			return;
		}
		
		wrappee.connected(address);
	}
	
	@Override
	public void close() {
		if (closed) {
			return;
		}
		
		closed = true;
		try {
			out.close();
			wrappee.closed();
		} catch (IOException ce) {
			wrappee.failed(ce);
		}
	}
	
	@Override
	public void closed() {
		close();
	}
}
