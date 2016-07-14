package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;

public interface SnmpRequestBuilder {
	SnmpRequest get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid, SnmpReceiver receiver);
}
