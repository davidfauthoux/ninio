package com.davfx.ninio.http;

import java.io.IOException;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.Trust;

public final class HttpServerConfigurator implements Closeable {
	public final Queue queue;
	private final boolean queueToClose;

	public Trust trust = null;
	public Address address = new Address("0.0.0.0", Http.DEFAULT_PORT);
	
	private HttpServerConfigurator(Queue queue, boolean queueToClose) {
		this.queue = queue;
		this.queueToClose = queueToClose;
	}
	
	public HttpServerConfigurator() throws IOException {
		this(new Queue(), true);
	}

	public HttpServerConfigurator(Queue queue) {
		this(queue, false);
	}

	@Override
	public void close() {
		if (queueToClose) {
			queue.close();
		}
	}
	
	public HttpServerConfigurator withTrust(Trust trust) {
		this.trust = trust;
		return this;
	}

	public HttpServerConfigurator withHost(String host) {
		address = new Address(host, address.getPort());
		return this;
	}
	public HttpServerConfigurator withPort(int port) {
		address = new Address(address.getHost(), port);
		return this;
	}
	public HttpServerConfigurator withAddress(Address address) {
		this.address = address;
		return this;
	}
}
