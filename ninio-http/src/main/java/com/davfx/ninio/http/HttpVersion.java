package com.davfx.ninio.http;

public enum HttpVersion {
	HTTP10("1.0"),
	HTTP11("1.1");

	private final String name;

	private HttpVersion(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name;
	}
}
