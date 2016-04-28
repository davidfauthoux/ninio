package com.davfx.ninio.http.v3;

public enum HttpMethod {
	GET("GET"),
	POST("POST"),
	HEAD("HEAD"),
	PUT("PUT"),
	DELETE("DELETE");

	private final String out;

	private HttpMethod(String out) {
		this.out = out;
	}

	@Override
	public String toString() {
		return out;
	}
}
