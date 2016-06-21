package com.davfx.ninio.core;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EchoReceiver implements Receiver {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(EchoReceiver.class);
	
	public EchoReceiver() {
	}
	
	@Override
	public void received(Connector conn, Address address, ByteBuffer buffer) {
		LOGGER.trace("Received {} bytes to echo", buffer.remaining());
		conn.send(address, buffer);
	}
}
