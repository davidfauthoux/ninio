package com.davfx.ninio.snmp.v3;

import java.util.Map;
import java.util.SortedMap;

import com.davfx.ninio.snmp.Oid;

public final class FromMapSnmpServerHandler implements SnmpServerHandler {
	private final SortedMap<Oid, String> map;
	
	public FromMapSnmpServerHandler(SortedMap<Oid, String> map) {
		this.map = map;
	}

	@Override
	public void from(Oid oid, SnmpServerHandler.Callback callback) {
		SortedMap<Oid, String> tail = map.tailMap(oid);
		for (Map.Entry<Oid, String> e : tail.entrySet()) {
			if (!callback.handle(e.getKey(), e.getValue())) {
				break;
			}
		}
	}
}
