package com.davfx.ninio.snmp;

public final class Result {
	private final Oid oid;
	private final String value;
	// private final OidValue value;

	public Result(Oid oid, String value) {
	// public Result(Oid oid, OidValue value) {
		this.oid = oid;
		this.value = value;
	}
	public Oid getOid() {
		return oid;
	}
	public String getValue() {
	// public OidValue getValue() {
		return value;
	}

	@Override
	public String toString() {
		return oid + ":" + value;
	}
}
