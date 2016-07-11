package com.davfx.ninio.core;

public interface Listening {
	interface Connection {
		Failing failing();
		Closing closing();
		Connecting connecting();
		Receiver receiver();
		Buffering buffering();
	}

	Connection connecting(Address from, Connector connector);
}