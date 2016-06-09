package com.davfx.ninio.core.v3;

public interface Listening {
	interface Connection {
		Failing failing();
		Closing closing();
		Connecting connecting();
		Receiver receiver();
	}

	Connection connecting(Address from, Connector connector);
}