package com.davfx.ninio.snmp;

public interface SnmpServerHandler {
	interface Callback {
		boolean handle(SnmpResult result);
	}
	void from(Oid oid, Callback callback);
}
