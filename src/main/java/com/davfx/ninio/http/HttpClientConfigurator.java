package com.davfx.ninio.http;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.ninio.common.SocketReadyFactory;
import com.davfx.ninio.common.SslReadyFactory;
import com.davfx.ninio.common.Trust;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class HttpClientConfigurator implements Closeable {
	
	private static final Config CONFIG = ConfigUtils.load(HttpClientConfigurator.class);
	
	public final Queue queue;
	private final boolean queueToClose;
	public final ScheduledExecutorService recyclersCloserExecutor;
	private final boolean recyclersCloserExecutorToShutdown;
	
	public int maxRedirectLevels = CONFIG.getInt("http.redirect.max");
	public double recyclersTimeToLive = ConfigUtils.getDuration(CONFIG, "http.recyclers.ttl");
	public double recyclersCheckTime = ConfigUtils.getDuration(CONFIG, "http.recyclers.check");

	public ReadyFactory readyFactory = new SocketReadyFactory();
	public ReadyFactory secureReadyFactory = null;
	public Trust trust = null;
	public Address address = new Address("localhost", Http.DEFAULT_PORT);

	private HttpClientConfigurator(Queue queue, boolean queueToClose, ScheduledExecutorService recyclersCloserExecutor, boolean recyclersCloserExecutorToShutdown) {
		this.queue = queue;
		this.queueToClose = queueToClose;
		this.recyclersCloserExecutor = recyclersCloserExecutor;
		this.recyclersCloserExecutorToShutdown = recyclersCloserExecutorToShutdown;
	}
	
	public HttpClientConfigurator() throws IOException {
		this(new Queue(), true, Executors.newSingleThreadScheduledExecutor(), true);
	}

	public HttpClientConfigurator(Queue queue) {
		this(queue, false, Executors.newSingleThreadScheduledExecutor(), true);
	}

	public HttpClientConfigurator(Queue queue, ScheduledExecutorService recyclersCloserExecutor) {
		this(queue, false, recyclersCloserExecutor, false);
	}

	@Override
	public void close() {
		if (queueToClose) {
			queue.close();
		}
		if (recyclersCloserExecutorToShutdown) {
			recyclersCloserExecutor.shutdown();
		}
	}
	
	public HttpClientConfigurator withTrust(Trust trust) {
		this.trust = trust;
		secureReadyFactory = new SslReadyFactory(trust);
		return this;
	}

	public HttpClientConfigurator withHost(String host) {
		address = new Address(host, address.getPort());
		return this;
	}
	public HttpClientConfigurator withPort(int port) {
		address = new Address(address.getHost(), port);
		return this;
	}
	public HttpClientConfigurator withAddress(Address address) {
		this.address = address;
		return this;
	}

	public HttpClientConfigurator override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	public HttpClientConfigurator overrideSecure(ReadyFactory readyFactory) {
		secureReadyFactory = readyFactory;
		return this;
	}
}
