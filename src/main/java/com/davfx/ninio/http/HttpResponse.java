package com.davfx.ninio.http;

import java.util.HashMap;
import java.util.Map;

public final class HttpResponse {
	
	private final int status;
	private final String reason;
	private final Map<String, String> headers;
	
	public HttpResponse(int status, String reason, Map<String, String> headers) {
		this.status = status;
		this.reason = reason;
		this.headers = headers;
	}
	public HttpResponse(int status, String reason) {
		this(status, reason, new HashMap<String, String>());
	}

	public int getStatus() {
		return status;
	}
	
	public String getReason() {
		return reason;
	}
	
	public Map<String, String> getHeaders() {
		return headers;
	}
}