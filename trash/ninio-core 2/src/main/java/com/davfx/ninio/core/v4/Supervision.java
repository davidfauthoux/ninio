package com.davfx.ninio.core.v4;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.dependencies.Dependencies;
import com.davfx.ninio.util.ClassThreadFactory;
import com.davfx.ninio.util.ConfigUtils;
import com.davfx.ninio.util.DateUtils;
import com.google.common.util.concurrent.AtomicDouble;
import com.typesafe.config.Config;

public final class Supervision {
	private static final Logger LOGGER = LoggerFactory.getLogger(Supervision.class);

	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(com.davfx.ninio.core.TcpSocket.class.getPackage().getName()); //TODO Set class to Supervision.class
	private static final double SUPERVISION_DISPLAY = ConfigUtils.getDuration(CONFIG, "supervision.tcp.display"); //TODO Change this config key

	private static double floorTime(double now, double period) {
    	double precision = 1000d;
    	long t = (long) (now * precision);
    	long d = (long) (period * precision);
    	return (t - (t % d)) / precision;
	}
	
	private static final ConcurrentHashMap<String, AtomicDouble> counters = new ConcurrentHashMap<>();
	static {
		double now = DateUtils.now();
		double startDisplay = SUPERVISION_DISPLAY - (now - floorTime(now, SUPERVISION_DISPLAY));
		
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ClassThreadFactory(Supervision.class, true));

		executor.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				for (Map.Entry<String, AtomicDouble> e : counters.entrySet()) {
					double v = e.getValue().getAndSet(0d);
					LOGGER.trace("[Supervision/{}] {}", e.getKey(), v);
				}
			}
		}, (long) (startDisplay * 1000d), (long) (SUPERVISION_DISPLAY * 1000d), TimeUnit.MILLISECONDS);
	}
	
	public static interface Supervise {
		void set(double value);
	}
	
	public static Supervise supervise(String counterName) {
		final AtomicDouble counter = new AtomicDouble(0d);
		counters.put(counterName, counter);
		return new Supervise() {
			@Override
			public void set(double value) {
				while (true) {
					double current = counter.get();
					if (current >= value) {
						break;
					}
					if (counter.compareAndSet(current, value)) {
						break;
					}
				}
			}
		};
	}
}
