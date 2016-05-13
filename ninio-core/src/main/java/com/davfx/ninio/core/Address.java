package com.davfx.ninio.core;

import java.util.Objects;

public final class Address {
	public static final String LOCALHOST = "127.0.0.1";
	public static final String ANY = "0.0.0.0";
	
	public static final char SEPARATOR = '^';
	
	private final String host;
	private final int port;

	@Deprecated
	public Address(int port) {
		this(null, port);
	}
	public Address(String host, int port) {
		if (host == null) {
			//TODO throw new NullPointerException("host");
		}
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
		/*
		if (host == null) {
			return SEPARATOR + "" + port;
		}
		*/
		return host + SEPARATOR + "" + port;
	}
	
	public static Address of(String hostPort) {
		int i = hostPort.indexOf(SEPARATOR);
		String h = hostPort.substring(0, i);
		/*
		if (h.isEmpty()) {
			h = null;
		}
		*/
		int p = Integer.parseInt(hostPort.substring(i + 1));
		return new Address(h, p);
	}

	/*%%%
	public static Address of(String hostPort, int defaultPort) {
		int i = hostPort.indexOf(SEPARATOR);
		if (i < 0) {
			return new Address(hostPort, defaultPort);
		}
		String h = hostPort.substring(0, i);
		if (h.isEmpty()) {
			h = null;
		}
		int p = Integer.parseInt(hostPort.substring(i + 1));
		return new Address(h, p);
	}
	*/

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
		return Objects.equals(a.host, host) && (a.port == port);
	}
}
