package com.davfx.ninio.telnet;

import java.io.IOException;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.SocketReadyFactory;

public final class TelnetClientConfigurator implements Closeable {
	public static final int DEFAULT_PORT = 23;

	public final Queue queue;
	private final boolean queueToClose;
	
	public Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);

	public ReadyFactory readyFactory = new SocketReadyFactory();

	private TelnetClientConfigurator(Queue queue, boolean queueToClose) {
		this.queue = queue;
		this.queueToClose = queueToClose;
	}
	
	public TelnetClientConfigurator() throws IOException {
		this(new Queue(), true);
	}

	public TelnetClientConfigurator(Queue queue) {
		this(queue, false);
	}

	@Override
	public void close() {
		if (queueToClose) {
			queue.close();
		}
	}
	
	public TelnetClientConfigurator(TelnetClientConfigurator configurator) {
		queueToClose = false;
		queue = configurator.queue;
		address = configurator.address;
		readyFactory = configurator.readyFactory;
	}
	
	public TelnetClientConfigurator withHost(String host) {
		address = new Address(host, address.getPort());
		return this;
	}
	public TelnetClientConfigurator withPort(int port) {
		address = new Address(address.getHost(), port);
		return this;
	}
	public TelnetClientConfigurator withAddress(Address address) {
		this.address = address;
		return this;
	}
	
	public TelnetClientConfigurator override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
}
