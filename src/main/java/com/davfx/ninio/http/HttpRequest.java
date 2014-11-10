package com.davfx.ninio.http;

import java.util.HashMap;
import java.util.Map;

import com.davfx.ninio.common.Address;

public final class HttpRequest {
	
	public static enum Method {
		GET("GET"),
		POST("POST"),
		HEAD("HEAD"),
		PUT("PUT"),
		DELETE("DELETE");
		
		private final String out;
		private Method(String out) {
			this.out = out;
		}
		@Override
		public String toString() {
			return out;
		}
	}

	private final Address address;
	private final boolean secure;
	private final Method method;
	private final String path;
	private final Map<String, String> headers;
	
	public HttpRequest(Address address, boolean secure, Method method, String path, Map<String, String> headers) {
		this.address = address;
		this.secure = secure;
		this.method = method;
		this.path = path;
		this.headers = headers;
	}
	
	public HttpRequest(Address address, boolean secure, Method method, String path) {
		this(address, secure, method, path, new HashMap<String, String>());
	}
	
	public Address getAddress() {
		return address;
	}
	public boolean isSecure() {
		return secure;
	}
	public Method getMethod() {
		return method;
	}
	public String getPath() {
		return path;
	}
	public Map<String, String> getHeaders() {
		return headers;
	}
}
