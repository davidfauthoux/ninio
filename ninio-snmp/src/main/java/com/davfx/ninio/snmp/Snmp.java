package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.DatagramReadyFactory;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.GlobalQueue;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class Snmp {
	
	private static final Config CONFIG = ConfigFactory.load();

	public static final int DEFAULT_PORT = 161;

	private Queue queue = null;
	private double timeoutFromBeginning = ConfigUtils.getDuration(CONFIG, "ninio.snmp.defaultTimeoutFromBeginning");
	private Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);
	private ReadyFactory readyFactory = new DatagramReadyFactory();
	private String community = CONFIG.getString("ninio.snmp.defaultCommunity");
	private AuthRemoteEngine authEngine = null;

	public Snmp() {
	}

	public Snmp withQueue(Queue queue) {
		this.queue = queue;
		return this;
	}
	
	public Snmp to(Address address) {
		this.address = address;
		return this;
	}
	
	public Snmp timeout(double timeoutFromBeginning) {
		this.timeoutFromBeginning = timeoutFromBeginning;
		return this;
	}
	
	public Snmp override(ReadyFactory readyFactory) {
		this.readyFactory = readyFactory;
		return this;
	}
	
	public Snmp withCommunity(String community) {
		this.community = community;
		return this;
	}
	public Snmp withLoginPassword(String authLogin, String authPassword, String authDigestAlgorithm, String privLogin, String privPassword, String privEncryptionAlgorithm) {
		authEngine = new AuthRemoteEngine(authLogin, authPassword, authDigestAlgorithm, privLogin, privPassword, privEncryptionAlgorithm);
		return this;
	}

	public SnmpClient create() {
		Queue q = queue;
		if (q == null) {
			q = GlobalQueue.get();
		}
		if (authEngine != null) {
			return new SnmpClient(queue, readyFactory, address, authEngine, timeoutFromBeginning);
		} else {
			return new SnmpClient(queue, readyFactory, address, community, timeoutFromBeginning);
		}
	}
}
