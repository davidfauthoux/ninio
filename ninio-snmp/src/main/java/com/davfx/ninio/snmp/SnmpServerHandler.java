package com.davfx.ninio.snmp;

import java.io.IOException;

import com.davfx.ninio.core.Address;

public interface SnmpServerHandler {
	interface Callback {
		boolean handle(SnmpResult result);
	}
	void from(Oid oid, Callback callback);
	
	void connected(Address address);
	void closed();
	void failed(IOException ioe);
}
