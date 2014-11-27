package com.davfx.ninio.ping;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;

import com.google.common.base.Splitter;

@Deprecated
public final class PingableAddress {
	public static PingableAddress from(String host) throws IOException {
		return new PingableAddress(InetAddress.getByName(host).getHostAddress());
	}
	
	public final byte[] ip;

	public PingableAddress(byte[] ip) {
		this.ip = ip;
	}

	public PingableAddress(String notation) {
		if (notation.indexOf('.') < 0) {
			List<String> s = Splitter.on(':').splitToList(notation);
			ip = new byte[s.size() * 2];
			int i = 0;
			for (String e : s) {
				int b = Integer.parseInt(e, 16);
				ip[i] = (byte) (b >>> 8);
				ip[i + 1] = (byte) b;
				i += 2;
			}
		} else {
			List<String> s = Splitter.on('.').splitToList(notation);
			ip = new byte[s.size()];
			int i = 0;
			for (String e : s) {
				ip[i] = (byte) Integer.parseInt(e);
				i++;
			}
		}
	}

	@Override
	public int hashCode() {
		int h = 0;
		int n = 0;
		for (byte b : ip) {
			h ^= (b << n) & 0xFF;
			n = (n + 8) % 32;
		}
		return h;
	}

	@Override
	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}
		if (o == this) {
			return true;
		}
		if (!(o instanceof PingableAddress)) {
			return false;
		}
		PingableAddress a = (PingableAddress) o;
		if (a.ip.length != ip.length) {
			return false;
		}
		for (int i = 0; i < ip.length; i++) {
			if (a.ip[i] != ip[i]) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		if (ip.length == 4) {
			for (int i = 0; i < ip.length; i++) {
				if (s.length() > 0) {
					s.append('.');
				}
				s.append(String.valueOf(ip[i] & 0xFF));
			}
		} else {
			for (int i = 0; i < ip.length; i += 2) {
				if (s.length() > 0) {
					s.append(':');
				}
				s.append(Integer.toHexString(((ip[i] & 0xFF) << 8) & (ip[i + 1] & 0xFF)));
			}
		}
		return s.toString();
	}
}
