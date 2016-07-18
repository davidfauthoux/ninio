package com.davfx.ninio.snmp;

import com.davfx.ninio.core.Failing;

public interface SnmpReceiver extends Failing {
	void received(SnmpResult result);
	void finished();
}
