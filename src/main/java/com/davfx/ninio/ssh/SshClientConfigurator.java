package com.davfx.ninio.ssh;

import java.io.IOException;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.SocketReadyFactory;

public final class SshClientConfigurator implements Closeable {
	public static final int DEFAULT_PORT = 22;

	public final Queue queue;
	private final boolean queueToClose;
	
	public Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);
	public String login = null;
	public String password = null;
	public SshPublicKey publicKey = null;

	public ReadyFactory readyFactory = new SocketReadyFactory();
	
	private SshClientConfigurator(Queue queue, boolean queueToClose) {
		this.queue = queue;
		this.queueToClose = queueToClose;
	}
	
	public SshClientConfigurator() throws IOException {
		this(new Queue(), true);
	}

	public SshClientConfigurator(Queue queue) {
		this(queue, false);
	}

	@Override
	public void close() {
		if (queueToClose) {
			queue.close();
		}
	}
	
	public SshClientConfigurator(SshClientConfigurator configurator) {
		queueToClose = false;
		queue = configurator.queue;
		address = configurator.address;
		login = configurator.login;
		password = configurator.password;
		publicKey = configurator.publicKey;
		readyFactory = configurator.readyFactory;
	}
	
	public SshClientConfigurator withLogin(String login) {
		this.login = login;
		return this;
	}
	public SshClientConfigurator withPassword(String password) {
		this.password = password;
		return this;
	}
	
	// TODO Test with PublicKeyLoader
	public SshClientConfigurator withPublicKey(SshPublicKey publicKey) {
		this.publicKey = publicKey;
		return this;
	}

	public SshClientConfigurator withHost(String host) {
		address = new Address(host, address.getPort());
		return this;
	}
	public SshClientConfigurator withPort(int port) {
		address = new Address(address.getHost(), port);
		return this;
	}
	public SshClientConfigurator withAddress(Address address) {
		this.address = address;
		return this;
	}
	
	public SshClientConfigurator override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
}
