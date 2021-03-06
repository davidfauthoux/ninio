package com.davfx.ninio.http;

public enum HttpMethod {
	GET("GET"),
	POST("POST"),
	HEAD("HEAD"),
	PUT("PUT"),
	DELETE("DELETE"),
	OPTIONS("OPTIONS");

	private final String out;

	private HttpMethod(String out) {
		this.out = out;
	}

	@Override
	public String toString() {
		return out;
	}
}
