package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Disconnectable;

public interface SnmpConnecter extends Disconnectable {
	void connect(SnmpConnection callback);
	Cancelable get(Address address, String community, AuthRemoteSpecification authRemoteSpecification, Oid oid, SnmpReceiver callback);
}
