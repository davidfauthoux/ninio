package com.davfx.ninio.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

@Deprecated
public final class ScriptUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(ScriptUtils.class);

	private static final Config CONFIG = ConfigFactory.load(ScriptUtils.class.getClassLoader());
	private static final String CONSOLE = CONFIG.getString("ninio.script.console");

	private ScriptUtils() {
	}
	
	static String functions() {
		return
			"var " + CONSOLE + " = {"
				+ "log: function(message) { " + ScriptUtils.class.getCanonicalName() + ".log(message); },"
				+ "debug: function(message) { " + ScriptUtils.class.getCanonicalName() + ".debug(message); }"
			+ "};"
		;
	}
	
	public static void log(String message) {
		System.out.println(message);
	}
	public static void debug(String message) {
		LOGGER.debug(message);
	}
}
