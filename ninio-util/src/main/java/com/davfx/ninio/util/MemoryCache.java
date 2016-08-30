package com.davfx.ninio.util;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class MemoryCache<K, V> {
	public static interface Builder<K, V> {
		Builder<K, V> expireAfterAccess(double expiration);
		Builder<K, V> expireAfterWrite(double expiration);
		MemoryCache<K, V> build();
	}
	
	public static <K, V> Builder<K, V> builder() {
		return new Builder<K, V>() {
			private double expirationAfterAccess = 0d;
			private double expirationAfterWrite = 0d;
			
			@Override
			public Builder<K, V> expireAfterAccess(double expiration) {
				expirationAfterAccess = expiration;
				return this;
			}
			@Override
			public Builder<K, V> expireAfterWrite(double expiration) {
				expirationAfterWrite = expiration;
				return this;
			}
			
			@Override
			public MemoryCache<K, V> build() {
				return new MemoryCache<>(expirationAfterAccess, expirationAfterWrite);
			}
		};
	}
	
	private static final class Element<V> {
		public double writeTimestamp;
		public double accessTimestamp;
		public final V v;
		public Element(V v) {
			this.v = v;
		}
	}

	private final double expirationAfterAccess;
	private final double expirationAfterWrite;
	private final Map<K, Element<V>> map = new HashMap<>();
	
	private MemoryCache(double expirationAfterAccess, double expirationAfterWrite) {
		this.expirationAfterAccess = expirationAfterAccess;
		this.expirationAfterWrite = expirationAfterWrite;
	}
	
	@Override
	public String toString() {
		StringBuilder b = new StringBuilder().append('{');
		boolean first = true;
		for (Map.Entry<K, Element<V>> e : map.entrySet()) {
			if (!first) {
				b.append(',');
			} else {
				first = false;
			}
			b.append(e.getKey()).append('=').append(e.getValue().v);
		}
		b.append('}');
		return b.toString();
	}
	
	public void put(K key, V value) {
		double now = DateUtils.now();
		Element<V> e = new Element<>(value);
		e.writeTimestamp = now;
		e.accessTimestamp = now;
		map.put(key, e);
	}
	
	public V get(K key) {
		double now = DateUtils.now();
		Element<V> e = map.get(key);
		if (expirationAfterAccess > 0d) {
			if ((now - e.accessTimestamp) >= expirationAfterAccess) {
				map.remove(key);
				return null;
			}
		}
		if (expirationAfterWrite > 0d) {
			if ((now - e.writeTimestamp) >= expirationAfterWrite) {
				map.remove(key);
				return null;
			}
		}
		e.accessTimestamp = now;
		return e.v;
	}

	public void remove(K key) {
		map.remove(key);
	}

	public void clear() {
		map.clear();
	}
	
	public Iterable<K> keys() {
		double now = DateUtils.now();

		if (expirationAfterAccess > 0d) {
			Iterator<Element<V>> i = map.values().iterator();
			while (i.hasNext()) {
				if ((now - i.next().accessTimestamp) > expirationAfterAccess) {
					i.remove();
				}
			}
		}
		if (expirationAfterWrite > 0d) {
			Iterator<Element<V>> i = map.values().iterator();
			while (i.hasNext()) {
				if ((now - i.next().writeTimestamp) > expirationAfterWrite) {
					i.remove();
				}
			}
		}

		return map.keySet();
	}
}
