package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Disconnectable;

public interface SnmpConnecter extends Disconnectable {
	void connect(SnmpConnection callback);
	SnmpRequestBuilder request();
}
