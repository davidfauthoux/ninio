package com.davfx.ninio.ping;

import java.io.IOException;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

public final class PingClientConfigurator implements Closeable {
	private static final Config CONFIG = ConfigUtils.load(PingClientConfigurator.class);
	public static final int DEFAULT_PORT = CONFIG.getInt("ping.port");
	
	public static final boolean SHELL_MODE;
	static {
		String mode = CONFIG.getString("ping.mode");
		if (mode.equals("java")) {
			SHELL_MODE = false;
		} else if (mode.equals("shell")) {
			SHELL_MODE = true;
		} else {
			throw new ConfigException.BadValue("ping.mode", "Invalid mode, only allowed: java|shell");
		}
	}

	public final Queue queue;
	private final boolean queueToClose;
	public ReadyFactory readyFactory;
	
	public Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);
	//%% public double timeout = ConfigUtils.getDuration(CONFIG, "ping.timeout");
	//%% public int maxSimultaneousClients = CONFIG.getInt("ping.maxSimultaneousClients");
	
	private PingClientConfigurator(Queue queue, boolean queueToClose) {
		this.queue = queue;
		this.queueToClose = queueToClose;
		readyFactory = new InternalPingServerReadyFactory(new CacheSyncPing(SHELL_MODE ? new ShellCommandSyncPing() : new PureJavaSyncPing()));
	}
	
	public PingClientConfigurator() throws IOException {
		this(new Queue(), true);
	}

	public PingClientConfigurator(Queue queue) {
		this(queue, false);
	}

	@Override
	public void close() {
		if (queueToClose) {
			queue.close();
		}
	}
	
	public PingClientConfigurator(PingClientConfigurator configurator) {
		queueToClose = false;
		queue = configurator.queue;
		address = configurator.address;
		//%% timeout = configurator.timeout;
		//%% maxSimultaneousClients = configurator.maxSimultaneousClients;
		readyFactory = configurator.readyFactory;
	}
	
	/*%%%%%
	public PingClientConfigurator withTimeout(double timeout) {
		this.timeout = timeout;
		return this;
	}

	public PingClientConfigurator withMaxSimultaneousClients(int maxSimultaneousClients) {
		this.maxSimultaneousClients = maxSimultaneousClients;
		return this;
	}
	*/

	public PingClientConfigurator withHost(String host) {
		address = new Address(host, address.getPort());
		return this;
	}
	public PingClientConfigurator withPort(int port) {
		address = new Address(address.getHost(), port);
		return this;
	}
	public PingClientConfigurator withAddress(Address address) {
		this.address = address;
		return this;
	}

	public PingClientConfigurator override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
}
