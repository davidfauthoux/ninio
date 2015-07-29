package com.davfx.ninio.script.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.common.Address;
import com.google.gson.JsonElement;

public final class Cache {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Cache.class);
	
	public static final class ForAddressCache {
		private static final class Caching {
			JsonElement result = null;
			IOException error = null;
			boolean closed = false;
			List<AsyncScriptFunctionCallbackManager> registered = new LinkedList<>();
		}
		
		private final Map<String, Caching> values = new HashMap<>();
		private final Object lock;
		public ForAddressCache(Object lock) {
			this.lock = lock;
		}
		
		public boolean register(String key, AsyncScriptFunctionCallbackManager m) {
			JsonElement result;
			IOException error;
			boolean closed;
			synchronized (lock) {
				Caching v = values.get(key);
				if (v == null) {
					values.put(key, new Caching());
					return true;
				}
	
				if ((v.result == null) && (v.error == null)) {
					v.registered.add(m);
					return false;
				}

				result = v.result;
				error = v.error;
				closed = v.closed;
			}
			
			if (result != null) {
				LOGGER.trace("Immediately using data from cache for: {}", key);
				m.done(result);
				return false;
			}
			if (error != null) {
				LOGGER.trace("Immediately using error from cache for: {}", key);
				m.failed(error);
				return false;
			}
			if (closed) {
				m.close();
				return false;
			}
			return false;
		}
		
		public void add(String key, JsonElement result) {
			synchronized (lock) {
				Caching v = values.get(key);
				if (v == null) {
					v = new Caching();
					values.put(key, v);
				}
				v.result = result;
			}
		}
		
		public void done(String key, JsonElement result) {
			List<AsyncScriptFunctionCallbackManager> registered;
			synchronized (lock) {
				Caching v = values.get(key);
				v.result = result;
				registered = v.registered;
				v.registered = new LinkedList<>();
			}

			for (AsyncScriptFunctionCallbackManager m : registered) {
				LOGGER.trace("Using data from cache for: {}", key);
				m.done(result);
			}
		}
		
		public void failed(String key, IOException error) {
			List<AsyncScriptFunctionCallbackManager> registered;
			synchronized (lock) {
				Caching v = values.get(key);
				v.error = error;
				registered = v.registered;
				v.registered = new LinkedList<>();
			}

			for (AsyncScriptFunctionCallbackManager m : registered) {
				m.failed(error);
			}
		}

		public void close(String key) {
			List<AsyncScriptFunctionCallbackManager> registered;
			synchronized (lock) {
				Caching v = values.get(key);
				v.closed = true;
				registered = v.registered;
				v.registered = new LinkedList<>();
			}

			for (AsyncScriptFunctionCallbackManager m : registered) {
				m.close();
			}
		}
	}
	
	private final Map<Address, ForAddressCache> cache = new HashMap<>();
	private final Object lock = new Object();

	public Cache() {
	}

	public ForAddressCache get(Address address) {
		synchronized (lock) {
			ForAddressCache ic = cache.get(address);
			if (ic == null) {
				ic = new ForAddressCache(lock);
				cache.put(address, ic);
			}
			return ic;
		}
	}
}