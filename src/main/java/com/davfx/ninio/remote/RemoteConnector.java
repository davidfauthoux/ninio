package com.davfx.ninio.remote;

import com.davfx.ninio.common.Closeable;

public interface RemoteConnector extends Closeable {
	/*%%%%%
	TelnetConnector withQueue(Queue queue);
	TelnetConnector withHost(String host);
	TelnetConnector withPort(int port);
	TelnetConnector withAddress(Address address);
	TelnetConnector override(ReadyFactory readyFactory);
	*/
	String getEol();
	void connect(RemoteClientHandler clientHandler);
}
