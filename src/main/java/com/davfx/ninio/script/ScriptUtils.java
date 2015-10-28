package com.davfx.ninio.script;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

public final class ScriptUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(ScriptUtils.class);

	private static final Config CONFIG = ConfigFactory.load();

	private static final String LOG_FUNCTION_NAME = CONFIG.getString("script.functions.log");
	private static final String LOG_PREFIX = CONFIG.getString("script.log.prefix");

	private ScriptUtils() {
	}
	
	static String functions() {
		return "var " + LOG_FUNCTION_NAME + " = function(message) { " + ScriptUtils.class.getCanonicalName() + ".log(message); }";
	}
	
	public static void log(String message) {
		LOGGER.debug(LOG_PREFIX + message);
	}
}
