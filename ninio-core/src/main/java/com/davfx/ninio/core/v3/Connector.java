package com.davfx.ninio.core.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;

public interface Connector {
	Connector connect();
	Connector disconnect();
	
	Connector send(Address address, ByteBuffer buffer);
	Connector failing(Failing failing);
	Connector closing(Closing closing);
	Connector connecting(Connecting connecting);
	Connector receiving(Receiver receiver);
}
