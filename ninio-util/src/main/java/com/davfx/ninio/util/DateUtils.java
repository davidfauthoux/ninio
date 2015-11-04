package com.davfx.ninio.util;

public final class DateUtils {
	private DateUtils() {
	}
	
	public static double now() {
		return System.currentTimeMillis() / 1000d;
	}
}
