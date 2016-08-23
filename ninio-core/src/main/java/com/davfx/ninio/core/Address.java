package com.davfx.ninio.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;

public final class Address {
	public static final byte[] LOCALHOST = new byte[] { 127, 0, 0, 1 };
	public static final byte[] ANY = new byte[] { 0, 0, 0, 0 };
	
	public static final char PORT_SEPARATOR = ':';
	
	public final byte[] ip;
	public final int port;

	public Address(byte[] ip, int port) {
		this.ip = ip;
		this.port = port;
	}
	
	private static String ipToString(byte[] ip) {
		try {
			return InetAddress.getByAddress(ip).getHostAddress();
		} catch (UnknownHostException e) {
			return null;
		}
	}
	
	@Override
	public String toString() {
		return ipToString(ip) + PORT_SEPARATOR + "" + port;
	}
	
	/*
	public static Address of(String hostPort) {
		int i = hostPort.indexOf(SEPARATOR);
		String h = hostPort.substring(0, i);
		int p = Integer.parseInt(hostPort.substring(i + 1));
		return new Address(h, p);
	}
	public static Address of(String host, int port) {
		int i = hostPort.indexOf(SEPARATOR);
		String h = hostPort.substring(0, i);
		int p = Integer.parseInt(hostPort.substring(i + 1));
		return new Address(h, p);
	}
	*/

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(ip), port);
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
		return Arrays.equals(a.ip, ip) && (a.port == port);
	}
}
