package com.davfx.ninio.core.v3;

public interface SocketBuilder {
	SocketBuilder failing(Failing failing);
	SocketBuilder closing(Closing closing);
	SocketBuilder connecting(Connecting connecting);
	SocketBuilder receiving(Receiver receiver);
}
