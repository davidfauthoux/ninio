package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;

public interface SnmpRequestBuilder {
	SnmpRequestBuilder community(String community);
	SnmpRequestBuilder auth(AuthRemoteSpecification authRemoteSpecification);

	interface SnmpRequestBuilderCancelable extends SnmpRequestBuilder, Cancelable {
	}
	
	SnmpRequestBuilderCancelable build(Address address, Oid oid);
	Cancelable receive(SnmpReceiver callback);
}
