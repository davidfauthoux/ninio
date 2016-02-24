package com.davfx.ninio.snmp;

import java.io.IOException;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.DatagramReadyFactory;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.util.ConfigUtils;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class Snmp {
	
	private static final Config CONFIG = ConfigFactory.load(Snmp.class.getClassLoader());

	public static final int DEFAULT_PORT = 161;

	private static final Queue DEFAULT_QUEUE = new Queue();

	private Queue queue = DEFAULT_QUEUE;
	private double timeoutFromBeginning = ConfigUtils.getDuration(CONFIG, "ninio.snmp.defaultTimeoutFromBeginning");
	private Address address = new Address(Address.LOCALHOST, DEFAULT_PORT);
	private ReadyFactory readyFactory = null;
	private String community = CONFIG.getString("ninio.snmp.defaultCommunity");
	private AuthRemoteSpecification authRemoteSpecification = null;
	private AuthRemoteEngine authRemoteEngine = null;

	private SnmpClientCache cache = null;
	
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
	public Snmp withAuth(AuthRemoteSpecification authRemoteSpecification) {
		this.authRemoteSpecification = authRemoteSpecification;
		if ((cache == null) && (authRemoteSpecification != null)) {
			authRemoteEngine = new AuthRemoteEngine(authRemoteSpecification);
		} else {
			authRemoteEngine = null;
		}
		return this;
	}

	public SnmpClient client() {
		return new SnmpClient(queue, (readyFactory == null) ? new DatagramReadyFactory(queue) : readyFactory, address, community, authRemoteEngine, timeoutFromBeginning);
	}
	
	public Snmp withCache(SnmpClientCache cache) {
		this.cache = cache;
		if ((cache == null) && (authRemoteSpecification != null)) {
			authRemoteEngine = new AuthRemoteEngine(authRemoteSpecification);
		} else {
			authRemoteEngine = null;
		}
		return this;
	}

	public void get(final Oid oid, final SnmpClientHandler.Callback.GetCallback getCallback) {
		if (cache == null) {
			final SnmpClient client = client();
			client.connect(new SnmpClientHandler() {
				@Override
				public void failed(IOException e) {
					getCallback.failed(e);
				}
				@Override
				public void close() {
					getCallback.failed(new IOException("Prematurely closed"));
				}
				@Override
				public void launched(final Callback callback) {
					callback.get(oid, new Callback.GetCallback() {
						@Override
						public void failed(IOException e) {
							callback.close();
							client.close();
							getCallback.failed(e);
						}
						@Override
						public void close() {
							callback.close();
							client.close();
							getCallback.close();
						}
						@Override
						public void result(Result result) {
							getCallback.result(result);
						}
					});
				}
			});
		} else {
			SnmpClientHandler.Callback client = cache.get(address, community, authRemoteSpecification, timeoutFromBeginning);
			client.get(oid, new SnmpClientHandler.Callback.GetCallback() {
				@Override
				public void failed(IOException e) {
					getCallback.failed(e);
				}
				@Override
				public void close() {
					getCallback.close();
				}
				@Override
				public void result(Result result) {
					getCallback.result(result);
				}
			});
		}
	}
}
