package com.davfx.ninio.snmp.v3;

public interface SnmpServerHandler {
	interface Callback {
		boolean handle(SnmpResult result);
	}
	void from(Oid oid, Callback callback);
}
