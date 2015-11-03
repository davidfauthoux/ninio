package com.davfx.ninio.util;

import java.util.concurrent.TimeUnit;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;

public final class ConfigUtils {
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
}
