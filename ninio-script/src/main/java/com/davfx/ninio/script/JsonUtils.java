package com.davfx.ninio.script;

import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

final class JsonUtils {
	private JsonUtils() {
	}
	
	public static String getString(JsonObject r, String key, String defaultValue) {
		JsonElement e = r.get(key);
		if (e == null) {
			return defaultValue;
		}
		if (e == JsonNull.INSTANCE) {
			return defaultValue;
		}
		return e.getAsString();
	}
	public static Pattern getPattern(JsonObject r, String key, Pattern defaultValue) {
		String e = getString(r, key, null);
		if (e == null) {
			return defaultValue;
		}
		return Pattern.compile(e, Pattern.DOTALL);
	}
	public static Integer getInt(JsonObject r, String key, Integer defaultValue) {
		JsonElement e = r.get(key);
		if (e == null) {
			return defaultValue;
		}
		if (e == JsonNull.INSTANCE) {
			return defaultValue;
		}
		return e.getAsInt();
	}
	public static Double getDouble(JsonObject r, String key, Double defaultValue) {
		JsonElement e = r.get(key);
		if (e == null) {
			return defaultValue;
		}
		if (e == JsonNull.INSTANCE) {
			return defaultValue;
		}
		return e.getAsDouble();
	}

	public static String getString(JsonObject r, String key) {
		return r.get(key).getAsString();
	}
	public static Pattern getPattern(JsonObject r, String key) {
		return Pattern.compile(getString(r, key));
	}
	public static Integer getInt(JsonObject r, String key) {
		return r.get(key).getAsInt();
	}
	public static Double getDouble(JsonObject r, String key) {
		return r.get(key).getAsDouble();
	}
}
