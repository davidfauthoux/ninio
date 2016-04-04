package com.davfx.ninio.snmp.v3;

import com.davfx.ninio.snmp.Oid;

public interface SnmpServerHandler {
	interface Callback {
		boolean handle(Oid oid, String value);
	}
	void from(Oid oid, Callback callback);
}
