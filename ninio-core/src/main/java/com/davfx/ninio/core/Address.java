package com.davfx.ninio.core;

public final class Address {
	public static final String LOCALHOST = "127.0.0.1";
	
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
		if (host == null) {
			return ":" + port;
		}
		return host + ":" + port;
	}
	
	public static Address of(String hostPort) {
		int i = hostPort.indexOf(':');
		String h = hostPort.substring(0, i);
		if (h.isEmpty()) {
			h = null;
		}
		int p = Integer.parseInt(hostPort.substring(i + 1));
		return new Address(h, p);
	}

	@Override
	public int hashCode() {
		if (host == null) {
			return port;
		}
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
		if (a.host == null) {
			if (host != null) {
				return false;
			}
		} else {
			if (host == null) {
				return false;
			}
			if (!a.host.equals(host)) {
				return false;
			}
		}
		if (a.port != port) {
			return false;
		}
		return true;
	}
}
