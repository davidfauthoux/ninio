package com.davfx.ninio.snmp.v3;

import com.davfx.ninio.core.v3.Address;
import com.davfx.ninio.core.v3.Failing;

public interface SnmpRequestBuilder {
	SnmpRequestBuilder failing(Failing failing);
	SnmpRequestBuilder receiving(SnmpReceiver receiver);
	SnmpRequest get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid);
}
