package com.davfx.ninio.snmp.v3;

import com.davfx.ninio.snmp.Result;

public interface SnmpReceiver {
	void received(Result result);
	void finished();
}
