package com.davfx.ninio.snmp.v3;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Failingable;
import com.davfx.ninio.snmp.AuthRemoteSpecification;
import com.davfx.ninio.snmp.Oid;

public interface SnmpRequest extends Failingable, SnmpReceivingable {
	void get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid);
}
