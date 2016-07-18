package com.davfx.ninio.core;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EchoReceiver implements Receiver {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(EchoReceiver.class);
	
	private final Sender sender;
	
	public EchoReceiver(Sender sender) {
		this.sender = sender;
	}
	
	@Override
	public void received(Address address, ByteBuffer buffer) {
		LOGGER.trace("Received {} bytes to echo", buffer.remaining());
		sender.send(address, buffer, new NopConnecterConnectingCallback());
	}
}
