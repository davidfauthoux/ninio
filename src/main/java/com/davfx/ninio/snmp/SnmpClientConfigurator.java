package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.ByAddressDatagramReadyFactory;
import com.davfx.ninio.common.ClassThreadFactory;
import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;

public final class SnmpClientConfigurator implements Closeable {
	private static final Config CONFIG = ConfigUtils.load(SnmpClientConfigurator.class);
	public static final int DEFAULT_PORT = 161;

	public final Queue queue;
	private final boolean queueToClose;
	public final ScheduledExecutorService repeatExecutor;
	private final boolean repeatExecutorToShutdown;
	
	public String community = "community";
	public AuthRemoteEngine authEngine = null;
	public Address address = new Address("localhost", DEFAULT_PORT);
	public int bulkSize = CONFIG.getInt("snmp.bulkSize");
	public double minTimeToRepeat = ConfigUtils.getDuration(CONFIG, "snmp.minTimeToRepeat");
	public int getLimit = CONFIG.getInt("snmp.getLimit");;

	public double repeatTime = ConfigUtils.getDuration(CONFIG, "snmp.repeatTime");

	public double timeoutFromBeginning = ConfigUtils.getDuration(CONFIG, "snmp.timeoutFromBeginning");
	//%% public double timeoutFromLastReception = ConfigUtils.getDuration(CONFIG, "snmp.timeoutFromLastReception");
	
	public double repeatRandomization = ConfigUtils.getDuration(CONFIG, "snmp.repeatRandomization");
	
	public ReadyFactory readyFactory;

	private SnmpClientConfigurator(Queue queue, boolean queueToClose, ScheduledExecutorService repeatExecutor, boolean repeatExecutorToShutdown) {
		this.queue = queue;
		this.queueToClose = queueToClose;
		this.repeatExecutor = repeatExecutor;
		this.repeatExecutorToShutdown = repeatExecutorToShutdown;
		readyFactory = new ByAddressDatagramReadyFactory(queue);
	}
	
	public SnmpClientConfigurator() throws IOException {
		this(new Queue(), true, Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(SnmpClientConfigurator.class)), true);
	}

	public SnmpClientConfigurator(Queue queue) {
		this(queue, false, Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(SnmpClientConfigurator.class)), true);
	}

	public SnmpClientConfigurator(Queue queue, ScheduledExecutorService repeatExecutor) {
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
	
	public SnmpClientConfigurator(SnmpClientConfigurator configurator) {
		queueToClose = false;
		repeatExecutorToShutdown = false;
		queue = configurator.queue;
		community = configurator.community;
		authEngine = configurator.authEngine;
		address = configurator.address;
		bulkSize = configurator.bulkSize;
		minTimeToRepeat = configurator.minTimeToRepeat;
		getLimit = configurator.getLimit;
		repeatTime = configurator.repeatTime;
		repeatExecutor = configurator.repeatExecutor;
		timeoutFromBeginning = configurator.timeoutFromBeginning;
		//%% timeoutFromLastReception = configurator.timeoutFromLastReception;
		repeatRandomization = configurator.repeatRandomization;
		readyFactory = configurator.readyFactory;
	}
	
	public SnmpClientConfigurator withMinTimeToRepeat(double minTimeToRepeat) {
		this.minTimeToRepeat = minTimeToRepeat;
		return this;
	}
	public SnmpClientConfigurator withRepeatTime(double repeatTime) {
		this.repeatTime = repeatTime;
		return this;
	}

	public SnmpClientConfigurator withTimeoutFromBeginning(double timeoutFromBeginning) {
		this.timeoutFromBeginning = timeoutFromBeginning;
		return this;
	}
	//%% public SnmpClientConfigurator withTimeoutFromLastReception(double timeoutFromLastReception) {
	//%% this.timeoutFromLastReception = timeoutFromLastReception;
	//%% return this;
	//%% }

	public SnmpClientConfigurator withRepeatRandomization(double repeatRandomization) {
		this.repeatRandomization = repeatRandomization;
		return this;
	}

	public SnmpClientConfigurator withCommunity(String community) {
		this.community = community;
		return this;
	}
	public SnmpClientConfigurator withLoginPassword(String authLogin, String authPassword, String authDigestAlgorithm, String privLogin, String privPassword, String privEncryptionAlgorithm) {
		authEngine = new AuthRemoteEngine(authLogin, authPassword, authDigestAlgorithm, privLogin, privPassword, privEncryptionAlgorithm);
		return this;
	}
	
	public SnmpClientConfigurator withHost(String host) {
		address = new Address(host, address.getPort());
		return this;
	}
	public SnmpClientConfigurator withPort(int port) {
		address = new Address(address.getHost(), port);
		return this;
	}
	public SnmpClientConfigurator withAddress(Address address) {
		this.address = address;
		return this;
	}
	
	public SnmpClientConfigurator withBulkSize(int bulkSize) {
		this.bulkSize = bulkSize;
		return this;
	}
	public SnmpClientConfigurator withGetLimit(int getLimit) {
		this.getLimit = getLimit;
		return this;
	}
	
	public SnmpClientConfigurator override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
}
