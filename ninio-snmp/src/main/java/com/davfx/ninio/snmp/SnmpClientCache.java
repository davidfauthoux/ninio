package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;
import com.davfx.ninio.snmp.SnmpClientHandler.Callback.GetCallback;

public final class SnmpClientCache implements Closeable, AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpClientCache.class);
	
	private static final class SnmpClientCacheKey {
		public final Address address;
		public final String community;
		public final AuthRemoteSpecification authRemoteSpecification;
		public SnmpClientCacheKey(Address address, String community, AuthRemoteSpecification authRemoteSpecification) {
			this.address = address;
			this.community = community;
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
	
	private static final class Task {
		public final Oid oid;
		public final SnmpClientHandler.Callback.GetCallback callback;
		public Task(Oid oid, GetCallback callback) {
			this.oid = oid;
			this.callback = callback;
		}
	}
	
	private static final class SnmpClientElement {
		public final SnmpClient client;
		public final List<Task> tasks = new LinkedList<>();
		public SnmpClientHandler.Callback callback = null;
		public SnmpClientElement(SnmpClient client) {
			this.client = client;
			
			client.connect(new SnmpClientHandler() {
				@Override
				public void failed(IOException e) {
					for (Task t : tasks) {
						t.callback.failed(e);
					}
					tasks.clear();
				}
				
				@Override
				public void close() {
					failed(new IOException("Closed"));
				}
				
				@Override
				public void launched(Callback callback) {
					for (Task t : tasks) {
						callback.get(t.oid, t.callback);
					}
					tasks.clear();
					SnmpClientElement.this.callback = callback;
				}
			});
		}
	}
	
	private final Map<SnmpClientCacheKey, SnmpClientElement> clients = new LinkedHashMap<>();

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
		for (SnmpClientElement c : clients.values()) {
			c.client.close();
		}
	}

	public SnmpClientHandler.Callback get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, double timeout) {
		SnmpClientElement c;
		SnmpClientCacheKey key = new SnmpClientCacheKey(address, community, authRemoteSpecification);
		c = clients.remove(key);
		if (c == null) {
			SnmpClient client = new SnmpClient(queue, udpReadyFactory, address, community, (authRemoteSpecification == null) ? null : new AuthRemoteEngine(authRemoteSpecification), timeout);
			LOGGER.debug("Creating SNMP connection: {}", address);
			c = new SnmpClientElement(client);
		} else {
			LOGGER.debug("Reusing SNMP connection: {}", address);
		}

		clients.put(key, c);
		
		if (clients.size() > max) {
			Iterator<Map.Entry<SnmpClientCacheKey, SnmpClientElement>> it = clients.entrySet().iterator();
			Map.Entry<SnmpClientCacheKey, SnmpClientElement> e = it.next();
			LOGGER.debug("Forcibly closing SNMP connection to: {}", e.getKey().address);
			for (Task t : e.getValue().tasks) {
				t.callback.failed(new IOException("Forcibly close (no more room, max = " + max + ")"));
			}
			e.getValue().client.close();
			it.remove();
		}
		
		final SnmpClientElement e = c;
		return new SnmpClientHandler.Callback() {
			@Override
			public void close() {
				// Ignored
			}
			@Override
			public void get(final Oid oid, final GetCallback getCallback) {
				queue.post(new Runnable() {
					@Override
					public void run() {
						if (e.callback != null) {
							e.callback.get(oid, getCallback);
						} else {
							e.tasks.add(new Task(oid, getCallback));
						}
					}
				});
			}
		};
	}
}
