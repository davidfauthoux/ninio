package com.davfx.ninio.ssh;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connection;

final class ReadingSshHeaderConnection implements Connection {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReadingSshHeaderConnection.class);
	
	private static final char EOL = SshSpecification.EOL.charAt(0);
	{
		if (SshSpecification.EOL.length() > 1) {
			throw new IllegalArgumentException("Invalid SSH EOL, should be one character only");
		}
	}
	
	public static interface Handler {
		void handle(String header);
	}
	
	private final Handler handler;
	private final Connection wrappee;
	private final StringBuilder lineBuilder = new StringBuilder();
	private String header = null;

	public ReadingSshHeaderConnection(Handler handler, Connection wrappee) {
		this.handler = handler;
		this.wrappee = wrappee;
	}

	@Override
	public void received(Address address, ByteBuffer b) {
		if (header != null) {
			wrappee.received(address, b);
			return;
		}
		
		while (b.hasRemaining()) {
			char c = (char) b.get();
			lineBuilder.append(c);
			if (c == EOL) {
				header = lineBuilder.toString().trim();
				LOGGER.trace("Header read: {}", header);
				handler.handle(header);
				break;
			}
		}
		if (b.hasRemaining()) {
			wrappee.received(address, b);
		}
	}
	
	@Override
	public void closed() {
		wrappee.closed();
	}
	
	@Override
	public void failed(IOException ioe) {
		wrappee.failed(ioe);
	}
	
	@Override
	public void connected(Address address) {
		wrappee.connected(address);
	}
}
