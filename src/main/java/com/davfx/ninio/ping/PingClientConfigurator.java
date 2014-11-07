package com.davfx.ninio.ping;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class PingClientConfigurator implements Closeable {
	private static final Config CONFIG = ConfigUtils.load(PingClientConfigurator.class);
	public static final int DEFAULT_PORT = CONFIG.getInt("ping.port");

	public final Queue queue;
	private final boolean queueToClose;
	public final ScheduledExecutorService repeatExecutor;
	private final boolean repeatExecutorToShutdown;
	public ReadyFactory readyFactory;
	
	public Address address = new Address("localhost", DEFAULT_PORT);
	public double minTimeToRepeat = ConfigUtils.getDuration(CONFIG, "ping.minTimeToRepeat");
	public double repeatTime = ConfigUtils.getDuration(CONFIG, "ping.repeatTime");
	public double timeoutFromBeginning = ConfigUtils.getDuration(CONFIG, "ping.timeoutFromBeginning");
	public int maxSimultaneousClients = CONFIG.getInt("ping.maxSimultaneousClients");

	private PingClientConfigurator(Queue queue, boolean queueToClose, ScheduledExecutorService repeatExecutor, boolean repeatExecutorToShutdown) {
		this.queue = queue;
		this.queueToClose = queueToClose;
		this.repeatExecutor = repeatExecutor;
		this.repeatExecutorToShutdown = repeatExecutorToShutdown;
		readyFactory = new InternalPingServerReadyFactory(maxSimultaneousClients);
	}
	
	public PingClientConfigurator() throws IOException {
		this(new Queue(), true, Executors.newSingleThreadScheduledExecutor(), true);
	}

	public PingClientConfigurator(Queue queue) {
		this(queue, false, Executors.newSingleThreadScheduledExecutor(), true);
	}

	public PingClientConfigurator(Queue queue, ScheduledExecutorService repeatExecutor) {
		this(queue, false, repeatExecutor, false);
	}

	@Override
	public void close() {
		if (queueToClose) {
			queue.close();
		}
		if (repeatExecutorToShutdown) {
			repeatExecutor.shutdown();
		}
	}
	
	public PingClientConfigurator(PingClientConfigurator configurator) {
		queueToClose = false;
		repeatExecutorToShutdown = false;
		queue = configurator.queue;
		address = configurator.address;
		minTimeToRepeat = configurator.minTimeToRepeat;
		repeatTime = configurator.repeatTime;
		repeatExecutor = configurator.repeatExecutor;
		timeoutFromBeginning = configurator.timeoutFromBeginning;
		maxSimultaneousClients = configurator.maxSimultaneousClients;
		readyFactory = configurator.readyFactory;
	}
	
	public PingClientConfigurator withMinTimeToRepeat(double minTimeToRepeat) {
		this.minTimeToRepeat = minTimeToRepeat;
		return this;
	}
	public PingClientConfigurator withRepeatTime(double repeatTime) {
		this.repeatTime = repeatTime;
		return this;
	}

	public PingClientConfigurator withTimeoutFromBeginning(double timeoutFromBeginning) {
		this.timeoutFromBeginning = timeoutFromBeginning;
		return this;
	}

	public PingClientConfigurator withMaxSimultaneousClients(int maxSimultaneousClients) {
		this.maxSimultaneousClients = maxSimultaneousClients;
		return this;
	}

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
