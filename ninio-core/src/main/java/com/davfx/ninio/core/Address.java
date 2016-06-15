package com.davfx.ninio.core;

import java.util.Objects;

public final class Address {
	public static final String LOCALHOST = "127.0.0.1";
	public static final String ANY = "0.0.0.0";
	
	public static final char SEPARATOR = ':';
	
	public final String host;
	public final int port;

	public Address(String host, int port) {
		this.host = host;
		this.port = port;
	}
	
	@Override
	public String toString() {
		return host + SEPARATOR + "" + port;
	}
	
	public static Address of(String hostPort) {
		int i = hostPort.indexOf(SEPARATOR);
		String h = hostPort.substring(0, i);
		int p = Integer.parseInt(hostPort.substring(i + 1));
		return new Address(h, p);
	}

	@Override
	public int hashCode() {
		return Objects.hash(host, port);
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
		return Objects.equals(a.host, host) && (a.port == port);
	}
}
