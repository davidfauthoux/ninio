package com.davfx.ninio.snmp.v3;

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
}
