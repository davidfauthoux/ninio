package com.davfx.ninio.ping;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.util.ConfigUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class CacheSyncPing implements SyncPing {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CacheSyncPing.class);

	private static final Config CONFIG = ConfigFactory.load();
	private static final int CACHE_SIZE = CONFIG.getInt("ping.cache.size");
	private static final double CACHE_EXPIRATION = ConfigUtils.getDuration(CONFIG, "ping.cache.expiration");

	private final SyncPing wrappee;
	private final Cache<String, Double> cache;

	public CacheSyncPing(SyncPing wrappee) {
		this.wrappee = wrappee;
		if (CACHE_SIZE == 0) {
			cache = null;
		} else {
			cache = CacheBuilder.newBuilder().maximumSize(CACHE_SIZE)
						.expireAfterWrite((long) CACHE_EXPIRATION, TimeUnit.SECONDS)
						.<String, Double>build();
		}
	}

	// Only the timeout given in the first call is used (cleared when cache is cleared)
	@Override
	public boolean isReachable(final String host, final double timeout) {
		try {
			double t = cache.get(host, new Callable<Double>() {
				@Override
				public Double call() {
					return wrappee.isReachable(host, timeout) ? timeout : Double.NaN;
				}
			});
			return !Double.isNaN(t);
		} catch (ExecutionException e) {
			LOGGER.debug("Execution error", e);
			return false;
		}
	}
	
	/*%%%
	private static void check(SyncPing ping, String host, double timeout) {
		long t = System.currentTimeMillis();
		boolean r = ping.isReachable(host, timeout);
		t = System.currentTimeMillis() - t;
		System.out.println(host + " = " + r + " in " + (t / 1000d) + " seconds");
	}
	public static void main(String[] args) {
		SyncPing p = new CacheSyncPing(new PureJavaSyncPing());
		check(p, "8.8.8.8", 0.2d);
		check(p, "8.8.8.8", 0.2d);
	}
	*/
}
