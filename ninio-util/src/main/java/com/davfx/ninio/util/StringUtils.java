package com.davfx.ninio.util;

public final class StringUtils {
	private StringUtils() {
	}
	
	public static String escape(String s, char escapeCharacter) {
		return s.replace("\\", "\\b").replace("" + escapeCharacter, "\\e");
	}

	public static String unescape(String s, char escapeCharacter) {
		return s.replace("\\e", "" + escapeCharacter).replace("\\b", "\\");
	}
}
