package com.davfx.ninio.snmp;

import java.util.Arrays;
import java.util.List;

import com.google.common.base.Splitter;

public final class Oid implements Comparable<Oid> {
	public final long[] raw;

	public Oid(String dotNotation) {
		List<String> s = Splitter.on('.').splitToList(dotNotation);
		//%% if (s.size() < 2) {
		//%% throw new IllegalArgumentException();
		//%% }
		raw = new long[s.size()];
		int i = 0;
		for (String e : s) {
			raw[i] = Long.parseLong(e);
			i++;
		}
	}

	public Oid(long[] raw) {
		//%% if (raw.length < 2) {
		//%% throw new IllegalArgumentException();
		//%% }
		this.raw = raw;
	}

	public Oid sub(Oid child) {
		if (child.raw.length < raw.length) {
			return null;
		}
		for (int i = 0; i < raw.length; i++) {
			if (raw[i] != child.raw[i]) {
				return null;
			}
		}
		long[] r = new long[child.raw.length - raw.length];
		System.arraycopy(child.raw, raw.length, r, 0, r.length);
		return new Oid(r);
	}

	public Oid append(Oid suffix) {
		long[] r = new long[raw.length + suffix.raw.length];
		System.arraycopy(raw, 0, r, 0, raw.length);
		System.arraycopy(suffix.raw, 0, r, raw.length, suffix.raw.length);
		return new Oid(r);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(raw);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (long e : raw) {
			if (b.length() > 0) {
				b.append('.');
			}
			b.append(String.valueOf(e));
		}
		return b.toString();
	}

	public boolean isPrefixOf(Oid oid) {
		if (oid.raw.length < raw.length) {
			return false;
		}
		for (int i = 0; i < raw.length; i++) {
			if (raw[i] != oid.raw[i]) {
				return false;
			}
		}
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null) {
			return false;
		}
		if (!(o instanceof Oid)) {
			return false;
		}
		Oid oid = (Oid) o;
		return Arrays.equals(oid.raw, raw);
	}
	
	@Override
	public int compareTo(Oid handledOid) {
		long[] handledOidRaw = handledOid.raw;
		int i = 0;
		while ((i < raw.length) && (i < handledOidRaw.length)) {
			long o = raw[i];
			long h = handledOidRaw[i];
			if (h > o) {
				return -1;
			}
			if (h < o) {
				return 1;
			}
			i++;
		}
		if (i < raw.length) {
			return 1;
		}
		if (i < handledOidRaw.length) {
			return -1;
		}
		return 0;
	}
}
