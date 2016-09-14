package com.davfx.ninio.http;

import java.util.Objects;

public final class HttpRequestAddress {
	public final String host;
	public final int port;
	public final boolean secure;

	public HttpRequestAddress(String host, int port, boolean secure) {
		this.host = host;
		this.port = port;
		this.secure = secure;
	}

	@Override
	public int hashCode() {
		return Objects.hash(host, port, secure);
	}

	@Override
	public String toString() {
		return host + ":" + port + "/" + secure;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof HttpRequestAddress)) {
			return false;
		}
		HttpRequestAddress other = (HttpRequestAddress) obj;
		return Objects.equals(host, other.host) && (port == other.port) && (secure == other.secure);
	}

}
