package com.davfx.ninio.snmp;

import java.util.Map;
import java.util.SortedMap;

public final class SnmpServerUtils {
	private SnmpServerUtils() {
	}
	
	public static SnmpServer.Handler from(final SortedMap<Oid, String> map) {
		return new SnmpServer.Handler() {
			@Override
			public void from(Oid oid, Callback callback) {
				SortedMap<Oid, String> tail = map.tailMap(oid);
				for (Map.Entry<Oid, String> e : tail.entrySet()) {
					if (!callback.handle(e.getKey(), e.getValue())) {
						break;
					}
				}
			}
		};
	}
}
