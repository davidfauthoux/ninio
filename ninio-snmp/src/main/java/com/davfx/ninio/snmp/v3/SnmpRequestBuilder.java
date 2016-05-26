package com.davfx.ninio.snmp.v3;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.snmp.AuthRemoteSpecification;
import com.davfx.ninio.snmp.Oid;

public interface SnmpRequestBuilder {
	SnmpRequestBuilder failing(Failing failing);
	SnmpRequestBuilder receiving(SnmpReceiver receiver);
	SnmpRequest get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid);
}
