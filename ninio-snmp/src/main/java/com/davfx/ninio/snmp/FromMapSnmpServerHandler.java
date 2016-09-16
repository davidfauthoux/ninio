package com.davfx.ninio.snmp;

import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;

import com.davfx.ninio.core.Address;

public final class FromMapSnmpServerHandler implements SnmpServerHandler {
	private final SortedMap<Oid, String> map;
	private final SnmpServerHandler wrappee;
	
	public FromMapSnmpServerHandler(SortedMap<Oid, String> map, SnmpServerHandler wrappee) {
		this.map = map;
		this.wrappee = wrappee;
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
	
	@Override
	public void connected(Address address) {
		if (wrappee != null) {
			wrappee.connected(address);
		}
	}
	@Override
	public void closed() {
		if (wrappee != null) {
			wrappee.closed();
		}
	}
	@Override
	public void failed(IOException ioe) {
		if (wrappee != null) {
			wrappee.failed(ioe);
		}
	}
}
