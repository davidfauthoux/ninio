package com.davfx.ninio.snmp.v3;

public interface SnmpReceiver {
	void received(SnmpResult result);
	void finished();
}
