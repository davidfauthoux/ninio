package com.davfx.ninio.telnet;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.core.SocketReadyFactory;
import com.davfx.ninio.util.GlobalQueue;

public final class Telnet {
	
	public static final int DEFAULT_PORT = 23;

	private Queue queue = null;
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
		Queue q = queue;
		if (q == null) {
			q = GlobalQueue.get();
		}
		return new TelnetClient(q, readyFactory, address);
	}
}
