package com.davfx.ninio.common;

public final class Address {
	private final String host;
	private final int port;

	public Address(int port) {
		this(null, port);
	}
	public Address(String host, int port) {
		this.host = host;
		this.port = port;
	}
	public String getHost() {
		return host;
	}
	public int getPort() {
		return port;
	}

	@Override
	public String toString() {
		return host + ":" + port;
	}

	@Override
	public int hashCode() {
		return host.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof Address)) {
			return false;
		}
		Address a = (Address) o;
		if (!a.host.equals(host)) {
			return false;
		}
		if (a.port != port) {
			return false;
		}
		return true;
	}
}
