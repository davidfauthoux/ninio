package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Failing;

public interface SnmpRequestBuilder {
	SnmpRequestBuilder failing(Failing failing);
	SnmpRequestBuilder receiving(SnmpReceiver receiver);
	SnmpRequest get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid);
}
