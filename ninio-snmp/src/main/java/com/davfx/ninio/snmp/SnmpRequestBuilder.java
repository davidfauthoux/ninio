package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;

public interface SnmpRequestBuilder extends Cancelable {
	SnmpRequestBuilder community(String community);
	SnmpRequestBuilder auth(AuthRemoteSpecification authRemoteSpecification);

	SnmpRequestBuilder build(Address address, Oid oid);
	SnmpRequestBuilder trap(Oid oid, String value);
	Cancelable receive(SnmpReceiver callback);
}
