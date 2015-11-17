package com.davfx.ninio.telnet;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.core.SocketReadyFactory;

public final class Telnet {
	
	public static final int DEFAULT_PORT = 23;

	private static final Queue DEFAULT_QUEUE = new Queue();

	private Queue queue = DEFAULT_QUEUE;
	private Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);
	private ReadyFactory readyFactory = new SocketReadyFactory();

	public Telnet() {
	}

	public Telnet withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	
	public Telnet to(Address address) {
		this.address = address;
		return this;
	}
	
	public Telnet override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	public TelnetClient client() {
		return new TelnetClient(queue, readyFactory, address);
	}
	
	public static TelnetSharingReadyFactory sharing() {
		return new TelnetSharingReadyFactory() {
			@Override
			public String eol() {
				return TelnetSpecification.EOL;
			}
			@Override
			public TelnetReady create(Queue queue, Address address) {
				return new TelnetClient(queue, new SocketReadyFactory(), address);
			}
		};
	}
}
