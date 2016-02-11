package com.davfx.ninio.snmp;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;

public final class SnmpClientCache implements Closeable, AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpClientCache.class);
	
	private static final class SnmpClientCacheKey {
		public final Address address;
		public final String community;
		public final AuthRemoteSpecification authRemoteSpecification;
		public SnmpClientCacheKey(Address address, String community) {
			this.address = address;
			this.community = community;
			authRemoteSpecification = null;
		}
		
		public SnmpClientCacheKey(Address address, AuthRemoteSpecification authRemoteSpecification) {
			this.address = address;
			community = null;
			this.authRemoteSpecification = authRemoteSpecification;
		}

		@Override
		public int hashCode() {
			return address.hashCode();
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (!(obj instanceof SnmpClientCacheKey)) {
				return false;
			}
			SnmpClientCacheKey other = (SnmpClientCacheKey) obj;
			return Objects.equals(address, other.address)
					&& Objects.equals(community, other.community)
					&& Objects.equals(authRemoteSpecification, other.authRemoteSpecification);
		}
	}
	
	private final Map<SnmpClientCacheKey, SnmpClient> clients = new LinkedHashMap<>();

	private final Queue queue;
	private final ReadyFactory udpReadyFactory;
	
	private final int max;
	
	public SnmpClientCache(Queue queue, ReadyFactory udpReadyFactory, int max) {
		this.queue = queue;
		this.udpReadyFactory = udpReadyFactory;
		this.max = max;
	}
	
	@Override
	public void close() {
		for (SnmpClient c : clients.values()) {
			c.close();
		}
	}

	public SnmpClient get(Address address, String community, double timeout) {
		SnmpClient client;
		SnmpClientCacheKey key = new SnmpClientCacheKey(address, community);
		client = clients.remove(key);
		if (client == null) {
			client = new SnmpClient(queue, udpReadyFactory, address, community, timeout);
			LOGGER.debug("Creating SNMP connection: {}", address);
		} else {
			LOGGER.debug("Reusing SNMP connection: {}", address);
		}
		clients.put(key, client);
		if (clients.size() > max) {
			Iterator<Map.Entry<SnmpClientCacheKey, SnmpClient>> it = clients.entrySet().iterator();
			Map.Entry<SnmpClientCacheKey, SnmpClient> e = it.next();
			LOGGER.debug("Forcibly closing SNMP connection to: {}", e.getKey().address);
			e.getValue().close();
			it.remove();
		}
		return client;
	}
	
	public SnmpClient get(Address address, AuthRemoteSpecification authRemoteSpecification, double timeout) {
		SnmpClient client;
		SnmpClientCacheKey key = new SnmpClientCacheKey(address, authRemoteSpecification);
		client = clients.remove(key);
		if (client == null) {
			AuthRemoteEngine engine = new AuthRemoteEngine(authRemoteSpecification);
			client = new SnmpClient(queue, udpReadyFactory, address, engine, timeout);
			LOGGER.debug("Creating SNMP connection: {}", address);
		} else {
			LOGGER.debug("Reusing SNMP connection: {}", address);
		}
		clients.put(key, client);
		if (clients.size() > max) {
			Iterator<Map.Entry<SnmpClientCacheKey, SnmpClient>> it = clients.entrySet().iterator();
			Map.Entry<SnmpClientCacheKey, SnmpClient> e = it.next();
			LOGGER.debug("Forcibly closing SNMP connection to: {}", e.getKey().address);
			e.getValue().close();
			it.remove();
		}
		return client;
	}
}
