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

@Deprecated
public final class OldPingClientConfigurator implements Closeable {
	private static final Config CONFIG = ConfigUtils.load(OldPingClientConfigurator.class);
	public static final int DEFAULT_PORT = CONFIG.getInt("ping.port");

	public final Queue queue;
	private final boolean queueToClose;
	public final ScheduledExecutorService repeatExecutor;
	private final boolean repeatExecutorToShutdown;
	public ReadyFactory readyFactory;
	
	public Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);
	public double minTimeToRepeat = ConfigUtils.getDuration(CONFIG, "ping.minTimeToRepeat");
	public double repeatTime = ConfigUtils.getDuration(CONFIG, "ping.repeatTime");
	public double timeoutFromBeginning = ConfigUtils.getDuration(CONFIG, "ping.timeoutFromBeginning");
	public int maxSimultaneousClients = CONFIG.getInt("ping.maxSimultaneousClients");
	
	private final OldSyncPing syncPing = new OldPureJavaSyncPing(); //TODO Implement differently and init according to conf

	private OldPingClientConfigurator(Queue queue, boolean queueToClose, ScheduledExecutorService repeatExecutor, boolean repeatExecutorToShutdown) {
		this.queue = queue;
		this.queueToClose = queueToClose;
		this.repeatExecutor = repeatExecutor;
		this.repeatExecutorToShutdown = repeatExecutorToShutdown;
		readyFactory = new OldInternalPingServerReadyFactory(maxSimultaneousClients, syncPing);
	}
	
	public OldPingClientConfigurator() throws IOException {
		this(new Queue(), true, Executors.newSingleThreadScheduledExecutor(), true);
	}

	public OldPingClientConfigurator(Queue queue) {
		this(queue, false, Executors.newSingleThreadScheduledExecutor(), true);
	}

	public OldPingClientConfigurator(Queue queue, ScheduledExecutorService repeatExecutor) {
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
	
	public OldPingClientConfigurator(OldPingClientConfigurator configurator) {
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
	
	public OldPingClientConfigurator withMinTimeToRepeat(double minTimeToRepeat) {
		this.minTimeToRepeat = minTimeToRepeat;
		return this;
	}
	public OldPingClientConfigurator withRepeatTime(double repeatTime) {
		this.repeatTime = repeatTime;
		return this;
	}

	public OldPingClientConfigurator withTimeoutFromBeginning(double timeoutFromBeginning) {
		this.timeoutFromBeginning = timeoutFromBeginning;
		return this;
	}

	public OldPingClientConfigurator withMaxSimultaneousClients(int maxSimultaneousClients) {
		this.maxSimultaneousClients = maxSimultaneousClients;
		return this;
	}

	public OldPingClientConfigurator withHost(String host) {
		address = new Address(host, address.getPort());
		return this;
	}
	public OldPingClientConfigurator withPort(int port) {
		address = new Address(address.getHost(), port);
		return this;
	}
	public OldPingClientConfigurator withAddress(Address address) {
		this.address = address;
		return this;
	}

	public OldPingClientConfigurator override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
}
