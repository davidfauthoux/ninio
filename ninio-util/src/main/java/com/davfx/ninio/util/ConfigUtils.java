package com.davfx.ninio.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;

public final class ConfigUtils {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ConfigUtils.class);
	
	private ConfigUtils() {
	}
	
	public static double getDuration(Config c, String key) {
		return c.getDuration(key, TimeUnit.NANOSECONDS) / 1_000_000_000d;
	}
	
	public static char getChar(Config c, String key) {
		String s = c.getString(key);
		if (s.length() != 1) {
			throw new ConfigException.BadValue(key, "Invalid value: " + s + ". Char value must be a string with only one character.");
		}
		return s.charAt(0);
	}
	
	private static String loadConfig(Class<?> clazz, String resource, boolean warn) throws IOException {
		InputStream i = clazz.getClassLoader().getResourceAsStream(resource + ".conf");
		if (i == null) {
			if (warn) {
				LOGGER.warn("Config file not found: {}", resource);
			}
			return "";
		}
		try (BufferedReader r = new BufferedReader(new InputStreamReader(i, Charsets.UTF_8))) {
			StringBuilder b = new StringBuilder();
			while (true) {
				String line = r.readLine();
				if (line == null) {
					return b.toString();
				}
				String l = line.trim();
				if (l.startsWith("include ")) {
					b.append(loadConfig(clazz, l.substring("include ".length()).trim(), true));
				} else {
					b.append(line);
				}
				b.append("\n");
			}
		}
	}

	private static final List<String> staticOverride = new LinkedList<>();
	
	public static Config load(Class<?> clazz) {
		String r;
		try {
			r = loadConfig(clazz, clazz.getPackage().getName(), true);
		} catch (Exception e) {
			throw new RuntimeException("Could not load package config", e);
		}

		String a;
		try {
			a = loadConfig(clazz, "configure", false);
		} catch (Exception e) {
			throw new RuntimeException("Could not load application config", e);
		}
		
		StringBuilder c = new StringBuilder();
		c.append(r);
		c.append('\n');
		c.append(a);
		synchronized (staticOverride) {
			for (String o : staticOverride) {
				c.append('\n');
				c.append(o);
			}
		}
		String conf = c.toString();

		// LOGGER.trace("Config: \n{}\n", conf);

		return ConfigFactory.parseString(conf).resolve().getConfig(clazz.getPackage().getName());
	}

	public static void override(String config) {
		synchronized (staticOverride) {
			staticOverride.add(config);
		}
	}
	
	/*%%
	public static final Config load() {
		return load("root");
	}
	public static final Config load(Class<?> clazz) {
		return load(clazz, clazz.getPackage().getName());
	}
	*/
}
