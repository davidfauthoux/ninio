package com.davfx.ninio.remote;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.davfx.ninio.common.Closeable;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class WaitingRemoteClientConfigurator implements Closeable {
	private static final Config CONFIG = ConfigUtils.load(WaitingRemoteClientConfigurator.class);
	
	public final ScheduledExecutorService callWithEmptyExecutor;
	private final boolean callWithEmptyExecutorToShutdown;
	
	public double endOfCommandTime = ConfigUtils.getDuration(CONFIG, "remote.waiting.endOfCommandTime");
	public double timeout = ConfigUtils.getDuration(CONFIG, "remote.waiting.timeout");
	
	public double callWithEmptyTime = ConfigUtils.getDuration(CONFIG, "remote.waiting.callWithEmptyTime");

	private WaitingRemoteClientConfigurator(ScheduledExecutorService callWithEmptyExecutor, boolean callWithEmptyExecutorToShutdown) {
		this.callWithEmptyExecutor = callWithEmptyExecutor;
		this.callWithEmptyExecutorToShutdown = callWithEmptyExecutorToShutdown;
	}
	
	public WaitingRemoteClientConfigurator() throws IOException {
		this(Executors.newSingleThreadScheduledExecutor(), true);
	}

	public WaitingRemoteClientConfigurator(ScheduledExecutorService callWithEmptyExecutor) {
		this(callWithEmptyExecutor, false);
	}

	@Override
	public void close() {
		if (callWithEmptyExecutorToShutdown) {
			callWithEmptyExecutor.shutdown();
		}
	}
	
	public WaitingRemoteClientConfigurator(WaitingRemoteClientConfigurator configurator) {
		callWithEmptyExecutorToShutdown = false;
		callWithEmptyExecutor = configurator.callWithEmptyExecutor;
		endOfCommandTime = configurator.endOfCommandTime;
		timeout = configurator.timeout;
		callWithEmptyTime = configurator.callWithEmptyTime;
	}
	
	public WaitingRemoteClientConfigurator withEndOfCommandTime(double endOfCommandTime) {
		this.endOfCommandTime = endOfCommandTime;
		return this;
	}
	public WaitingRemoteClientConfigurator withTimeout(double timeout) {
		this.timeout = timeout;
		return this;
	}

	public WaitingRemoteClientConfigurator withCallWithEmptyTime(double callWithEmptyTime) {
		this.callWithEmptyTime = callWithEmptyTime;
		return this;
	}

}
