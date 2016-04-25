package com.davfx.ninio.core.v3;

public interface Listening {
	public interface ConnectorBuilder {
		ConnectorBuilder failing(Failing failing);
		ConnectorBuilder closing(Closing closing);
		ConnectorBuilder connecting(Connecting connecting);
		ConnectorBuilder receiving(Receiver receiver);
	}

	void connecting(ConnectorBuilder connectable);
}