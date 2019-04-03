package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;

public interface SnmpRequestBuilder extends Cancelable { // extends Cancelable is deprecated
	SnmpRequestBuilder community(String community);
	SnmpRequestBuilder auth(AuthRemoteSpecification authRemoteSpecification);

	SnmpRequestBuilder build(Address address, Oid oid);
	SnmpRequestBuilder add(Oid oid, String value);

	Cancelable call(SnmpCallType type, SnmpReceiver callback);
}
