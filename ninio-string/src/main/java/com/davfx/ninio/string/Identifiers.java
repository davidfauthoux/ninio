package com.davfx.ninio.string;

import java.security.SecureRandom;

import com.davfx.ninio.string.dependencies.Dependencies;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

public final class Identifiers {
	private static final Config CONFIG = ConfigUtils.load(new Dependencies()).getConfig(Identifiers.class.getPackage().getName());
	private static final int DEFAULT_SIZE = CONFIG.getBytes("identifiers.size.default").intValue();

	private Identifiers() {
	}
	
	private static final SecureRandom RANDOM = new SecureRandom();

	public static String identifier(int size) {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < size; i++) {
			if (RANDOM.nextBoolean()) {
				b.append((char) ('a' + RANDOM.nextInt('z' - 'a' + 1)));
			} else {
				b.append((char) ('A' + RANDOM.nextInt('Z' - 'A' + 1)));
			}
		}
		return b.toString();
	}
	public static String identifier() {
		return identifier(DEFAULT_SIZE);
	}
}
