package com.davfx.ninio.snmp.v3;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.snmp.AuthRemoteSpecification;
import com.davfx.ninio.snmp.Oid;

public interface SnmpReceiverRequest {
	void get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid);
}
