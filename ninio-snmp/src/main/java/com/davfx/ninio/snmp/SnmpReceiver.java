package com.davfx.ninio.snmp;

import java.io.IOException;

public interface SnmpReceiver {
	void received(SnmpResult result);
	void finished();
	void failed(IOException ioe);
}
