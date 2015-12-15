package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Queue;
import com.davfx.ninio.core.ReadyFactory;

public final class SnmpCache {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SnmpCache.class);
	
	public static interface Filter {
		double cache(Address address, Oid oid);
	}
	
	// Memory saving!
	private final Map<Oid, Oid> internOids = new HashMap<>();
	private final Map<String, String> internStrings = new HashMap<>();
	
	private Oid internOid(Oid oid) {
		Oid intern = internOids.get(oid);
		if (intern == null) {
			intern = oid;
			internOids.put(intern, intern);
		}
		return intern;
	}
	private String internString(String s) {
		String intern = internStrings.get(s);
		if (intern == null) {
			intern = s;
			internStrings.put(intern, intern);
		}
		return intern;
	}
	private Result internResult(Result result) {
		return new Result(internOid(result.getOid()), internString(result.getValue()));
	}
	
	private static final class DataCacheElement {
		public final List<Result> results = new LinkedList<>();
		public List<SnmpClientHandler.Callback.GetCallback> callbacks = new LinkedList<>();
		public final double timestamp;
		public DataCacheElement(double timestamp) {
			this.timestamp = timestamp;
		}
	}

	private static final class CacheElement {
		public IOException ioe = null;
		public double timestamp;
		public final Map<Oid, DataCacheElement> data = new HashMap<>();
		public CacheElement() {
		}
	}
	private final Map<Address, CacheElement> cache = new HashMap<>();

	private final Queue queue;
	private final ReadyFactory readyFactory;
	private final Filter filter;
	
	public SnmpCache(Queue queue, ReadyFactory readyFactory, Filter filter) {
		this.queue = queue;
		this.readyFactory = readyFactory;
		this.filter = filter;
	}
	
	private static double now() {
		return System.currentTimeMillis() / 1000d;
	}

	// May be called often to save memory
	public void clear() {
		Iterator<CacheElement> i = cache.values().iterator();
		while (i.hasNext()) {
			CacheElement g = i.next();

			Iterator<DataCacheElement> j = g.data.values().iterator();
			while (j.hasNext()) {
				DataCacheElement e = j.next();
				if (e.callbacks == null) {
					j.remove();
				}
			}
			
			if (g.data.isEmpty()) {
				i.remove();
			}
		}
	}
	
	public void get(final Address address, final Oid oid, final SnmpClientHandler.Callback.GetCallback getCallback) {
		final double delayToDiscard = filter.cache(address, oid);
		if (Double.isNaN(delayToDiscard)) {
			LOGGER.trace("Not caching {}", oid);
			new Snmp().override(readyFactory).withQueue(queue).to(address).get(oid, getCallback);
			return;
		}
		
		queue.post(new Runnable() {
			@Override
			public void run() {
				double now = now();
				
				CacheElement f = cache.get(address);
				if (f == null) {
					f = new CacheElement();
					cache.put(address, f);
				}
				if (f.ioe != null) {
					if ((now - f.timestamp) > delayToDiscard) {
						f.ioe = null;
					}
				}
				if (f.ioe != null) {
					getCallback.failed(f.ioe);
					return;
				}
				
				DataCacheElement e = f.data.get(oid);
				if (e != null) {
					if (e.callbacks == null) {
						if ((now - e.timestamp) > delayToDiscard) {
							e = null;
						}
					}
				}
				if (e == null) {
					e = new DataCacheElement(now);
					f.data.put(oid, e);
				}
				
				if (e.callbacks == null) {
					for (Result r : e.results) {
						getCallback.result(r);
					}
					getCallback.close();
				} else {
					if (e.callbacks.isEmpty()) {
						e.callbacks.add(getCallback);
						call(address, oid, e, f);
					} else {
						e.callbacks.add(getCallback);
					}
				}
			}
		});
	}
	
	private void call(final Address address, Oid oid, final DataCacheElement e, final CacheElement f) {
		LOGGER.trace("Actually calling {}", oid);
		new Snmp().override(readyFactory).withQueue(queue).to(address).get(oid, new SnmpClientHandler.Callback.GetCallback() {
			@Override
			public void failed(IOException ioe) {
				f.ioe = ioe;
				f.timestamp = now();
				for (SnmpClientHandler.Callback.GetCallback c : e.callbacks) {
					c.failed(ioe);
				}
				e.callbacks = null;
			}
			@Override
			public void close() {
				for (SnmpClientHandler.Callback.GetCallback c : e.callbacks) {
					c.close();
				}
				e.callbacks = null;
			}
			@Override
			public void result(Result result) {
				double now = now();

				Result internResult = internResult(result);

				Oid oid = internOid(result.getOid());
				
				final double delayToDiscard = filter.cache(address, oid);
				if (!Double.isNaN(delayToDiscard)) {
					// Cache single value
					DataCacheElement r = f.data.get(oid);
					if (r != null) {
						if (r.callbacks == null) {
							if ((now - r.timestamp) > delayToDiscard) {
								r = null;
							}
						}
					}
					if (r == null) {
						r = new DataCacheElement(e.timestamp);
						LOGGER.trace("Caching single value: {} = {}", oid, result);
						f.data.put(oid, r);
						r.results.add(internResult);
						r.callbacks = null;
					}
				}

				e.results.add(internResult);
				for (SnmpClientHandler.Callback.GetCallback c : e.callbacks) {
					c.result(result);
				}
			}
		});
	}
}
