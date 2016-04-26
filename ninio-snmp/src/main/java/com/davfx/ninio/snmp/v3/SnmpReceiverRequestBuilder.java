package com.davfx.ninio.snmp.v3;

import com.davfx.ninio.core.v3.Failing;

public interface SnmpReceiverRequestBuilder {
	SnmpReceiverRequestBuilder failing(Failing failing);
	SnmpReceiverRequestBuilder receiving(SnmpReceiver receiver);
	SnmpReceiverRequest build();
}
