package com.davfx.ninio.ftp;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.SocketReadyFactory;

public final class FtpClientConfigurator {
	public static final int DEFAULT_PORT = 21;

	public Queue queue = null;
	public String login = "user";
	public String password = "pass";
	public Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);
	public String host = null;
	public int port = -1;

	public ReadyFactory readyFactory = new SocketReadyFactory();

	public FtpClientConfigurator() {
	}
	
	public FtpClientConfigurator withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	public FtpClientConfigurator withLogin(String login) {
		this.login = login;
		return this;
	}
	public FtpClientConfigurator withPassword(String password) {
		this.password = password;
		return this;
	}
	
	public FtpClientConfigurator withHost(String host) {
		this.host = host;
		return this;
	}
	public FtpClientConfigurator withPort(int port) {
		this.port = port;
		return this;
	}
	public FtpClientConfigurator withAddress(Address address) {
		this.address = address;
		return this;
	}
	
	public FtpClientConfigurator override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
}
