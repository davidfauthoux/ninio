package com.davfx.ninio.snmp;

public interface SnmpReceiver {
	void received(SnmpResult result);
	void finished();
}
