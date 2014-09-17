package com.davfx.ninio.telnet;

import com.davfx.ninio.common.Address;
import com.davfx.ninio.common.Queue;
import com.davfx.ninio.common.ReadyFactory;

public interface TelnetConnector {
	String getEol();
	
	TelnetConnector withQueue(Queue queue);
	TelnetConnector withHost(String host);
	TelnetConnector withPort(int port);
	TelnetConnector withAddress(Address address);
	TelnetConnector override(ReadyFactory readyFactory);

	void connect(TelnetClientHandler clientHandler);
}
