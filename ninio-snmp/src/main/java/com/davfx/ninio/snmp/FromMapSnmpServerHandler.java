package com.davfx.ninio.snmp;

import java.util.Map;
import java.util.SortedMap;

public final class FromMapSnmpServerHandler implements SnmpServerHandler {
	private final SortedMap<Oid, String> map;
	
	public FromMapSnmpServerHandler(SortedMap<Oid, String> map) {
		this.map = map;
	}

	@Override
	public void from(Oid oid, SnmpServerHandler.Callback callback) {
		SortedMap<Oid, String> tail = map.tailMap(oid);
		for (Map.Entry<Oid, String> e : tail.entrySet()) {
			if (!callback.handle(new SnmpResult(e.getKey(), e.getValue()))) {
				break;
			}
		}
	}
}
