package com.davfx.ninio.common;

import java.io.IOException;
import java.net.InetSocketAddress;

final class AddressUtils {
	private AddressUtils() {
	}
	public static InetSocketAddress toConnectableInetSocketAddress(Address address) throws IOException {
		if (address.getHost() == null) {
			return null;
		}
		InetSocketAddress a = new InetSocketAddress(address.getHost(), address.getPort()); // Note this call blocks to resolve host (DNS resolution)
		if (a.isUnresolved()) {
			throw new IOException("Unresolved address: " + address.getHost() + ":" + address.getPort());
		}
		return a;
	}

	public static InetSocketAddress toBindableInetSocketAddress(Address address) throws IOException {
		if (address.getHost() != null) {
			return toConnectableInetSocketAddress(address);
		}
		return new InetSocketAddress(address.getPort());
	}

}
