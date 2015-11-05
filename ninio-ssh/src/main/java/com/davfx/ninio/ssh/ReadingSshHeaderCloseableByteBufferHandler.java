package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.CloseableByteBufferHandler;

final class ReadingSshHeaderCloseableByteBufferHandler implements CloseableByteBufferHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReadingSshHeaderCloseableByteBufferHandler.class);
	
	public static interface Handler {
		void handle(String header);
	}
	
	private final Handler handler;
	private final CloseableByteBufferHandler wrappee;
	private final StringBuilder lineBuilder = new StringBuilder();
	private String header = null;

	public ReadingSshHeaderCloseableByteBufferHandler(Handler handler, CloseableByteBufferHandler wrappee) {
		this.handler = handler;
		this.wrappee = wrappee;
	}
	
	@Override
	public void close() {
		wrappee.close();
	}

	@Override
	public void handle(Address address, ByteBuffer b) {
		if (header != null) {
			wrappee.handle(address, b);
			return;
		}
		
		while (b.hasRemaining()) {
			char c = (char) b.get();
			lineBuilder.append(c);
			if (c == '\n') {
				header = lineBuilder.toString().trim();
				LOGGER.trace("Header read: {}", header);
				handler.handle(header);
				break;
			}
		}
		if (b.hasRemaining()) {
			wrappee.handle(address, b);
		}
	}
}
