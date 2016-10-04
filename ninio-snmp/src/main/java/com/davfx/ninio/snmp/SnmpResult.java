package com.davfx.ninio.snmp;

import java.util.Objects;

public final class SnmpResult {
	public final Oid oid;
	public final String value;

	public SnmpResult(Oid oid, String value) {
		this.oid = oid;
		this.value = value;
	}

	@Override
	public String toString() {
		return oid + ":" + value;
	}

	@Override
	public int hashCode() {
		return Objects.hash(oid, value);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (!(obj instanceof SnmpResult)) {
			return false;
		}
		SnmpResult other = (SnmpResult) obj;
		return Objects.equals(oid, other.oid) && Objects.equals(value, other.value);
	}
}
