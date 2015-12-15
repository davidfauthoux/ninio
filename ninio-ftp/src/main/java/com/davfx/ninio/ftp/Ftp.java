package com.davfx.ninio.ftp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.core.SocketReadyFactory;

public final class Ftp {
	
	public static final int DEFAULT_PORT = 21;

	private static final Queue DEFAULT_QUEUE = new Queue();

	private Queue queue = DEFAULT_QUEUE;
	private Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);
	private ReadyFactory readyFactory = null;

	private String login = null;
	private String password = null;
	
	public Ftp() {
	}

	public Ftp withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	
	public Ftp to(Address address) {
		this.address = address;
		return this;
	}
	
	public Ftp withLogin(String login) {
		this.login = login;
		return this;
	}
	public Ftp withPassword(String password) {
		this.password = password;
		return this;
	}
	
	public Ftp override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	public FtpClient client() {
		return new FtpClient(queue, (readyFactory == null) ? new SocketReadyFactory(queue) : readyFactory, address, login, password);
	}
}
