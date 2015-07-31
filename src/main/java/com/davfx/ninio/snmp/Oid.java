package com.davfx.ninio.snmp;

import java.util.List;

import com.google.common.base.Splitter;

public final class Oid {
	private final int[] raw;

	public Oid(String dotNotation) {
		List<String> s = Splitter.on('.').splitToList(dotNotation);
		//%% if (s.size() < 2) {
		//%% throw new IllegalArgumentException();
		//%% }
		raw = new int[s.size()];
		int i = 0;
		for (String e : s) {
			raw[i] = Integer.parseInt(e);
			i++;
		}
	}

	public Oid(int[] raw) {
		//%% if (raw.length < 2) {
		//%% throw new IllegalArgumentException();
		//%% }
		this.raw = raw;
	}

	public int[] getRaw() {
		return raw;
	}
	
	public Oid sub(Oid child) {
		if (child.raw.length <= raw.length) {
			return null;
		}
		for (int i = 0; i < raw.length; i++) {
			if (raw[i] != child.raw[i]) {
				return null;
			}
		}
		int[] r = new int[child.raw.length - raw.length];
		System.arraycopy(child.raw, raw.length, r, 0, r.length);
		return new Oid(r);
	}

	public Oid append(Oid suffix) {
		int[] r = new int[raw.length + suffix.raw.length];
		System.arraycopy(raw, 0, r, 0, raw.length);
		System.arraycopy(suffix.raw, 0, r, raw.length, suffix.raw.length);
		return new Oid(r);
	}

	@Override
	public int hashCode() {
		int h = 0;
		for (int e : raw) {
			h += e;
		}
		return (h / raw.length);
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		for (int e : raw) {
			if (b.length() > 0) {
				b.append('.');
			}
			b.append(String.valueOf(e));
		}
		return b.toString();
	}

	public boolean isPrefix(Oid oid) {
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
		if (oid.raw.length != raw.length) {
			return false;
		}
		for (int i = 0; i < raw.length; i++) {
			if (raw[i] != oid.raw[i]) {
				return false;
			}
		}
		return true;
	}
}
