package com.davfx.ninio.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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
	
	//
	
	private static InputStream getResource(Dependencies dependencies, String resource) {
		InputStream i = dependencies.getClass().getClassLoader().getResourceAsStream(resource + ".conf");
		if (i == null) {
			for (Dependencies d : dependencies.dependencies()) {
				i = getResource(d, resource);
				if (i != null) {
					return i;
				}
			}
			return null;
		} else {
			return i;
		}
	}

	private static String loadConfig(Dependencies dependencies, String resource, boolean warn) throws IOException {
		File rootDir = new File(".");
		LOGGER.trace("Absolute path: {}", rootDir.getAbsolutePath());
		InputStream i;
		File f = new File(rootDir, resource + ".conf");
		if (f.exists()) {
			i = new FileInputStream(f);
		} else {
			i = getResource(dependencies, resource);
			if (i == null) {
				if (warn) {
					LOGGER.warn("Config file not found: {}", resource);
					throw new RuntimeException("Config file not found: " + resource);
				}
				return "";
			}
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
					b.append(loadConfig(dependencies, l.substring("include ".length()).trim(), true));
				} else {
					b.append(line);
				}
				b.append("\n");
			}
		}
	}

	public static final class Override {
		private final List<String> override = new LinkedList<>();
		public Override() {
		}
		
		public Override add(String config) {
			override.add(config);
			return this;
		}
		public Override add(String key, String value) {
			override.add(key + " = \"" + value.replace("\"", "\\\""));
			return this;
		}
	}

	// Application conf (configure.conf is used over resource)
	public static synchronized Config load(Dependencies dependencies, String resource) {
		return load(dependencies, resource, new Override());
	}
	
	private static void gatherDependencies(Dependencies dependencies, List<Dependencies> l) {
		for (Dependencies d : dependencies.dependencies()) {
			gatherDependencies(d, l);
		}
		if (!l.contains(dependencies)) {
			l.add(dependencies);
		}
	}
	
	public static synchronized Config load(Dependencies dependencies, String resource, Override override) {
		StringBuilder c = new StringBuilder();

		List<Dependencies> l = new LinkedList<>();
		gatherDependencies(dependencies, l);
		for (Dependencies d : l) {
			String packageName = d.getClass().getPackage().getName();
			LOGGER.debug("Dependency conf: {}", packageName);
			if (!packageName.endsWith(".dependencies")) {
				throw new RuntimeException("Must ends with '.dependencies': " + packageName);
			}
			packageName = packageName.substring(0, packageName.length() - ".dependencies".length());
			String r;
			try {
				r = loadConfig(d, packageName, true);
			} catch (Exception e) {
				throw new RuntimeException("Could not load package config", e);
			}
			c.append(r);
		}
		
		if (resource != null) {
			String r;
			try {
				r = loadConfig(dependencies, resource, true);
			} catch (Exception e) {
				throw new RuntimeException("Could not load package config", e);
			}
			c.append(r);
		}

		String a;
		try {
			a = loadConfig(dependencies, "configure", false);
		} catch (Exception e) {
			throw new RuntimeException("Could not load application config", e);
		}
		c.append('\n');
		c.append(a);

		for (String o : override.override) {
			c.append('\n');
			c.append(o);
		}

		String conf = c.toString();

		LOGGER.debug("Config: \n{}\n", conf);

		return ConfigFactory.parseString(conf).resolve();
	}

	// Static conf, overriding is done with configure.conf
	public static synchronized Config load(Dependencies dependencies, Class<?> clazz) {
		return load(dependencies, null, new Override()).getConfig(clazz.getPackage().getName());
	}
}
