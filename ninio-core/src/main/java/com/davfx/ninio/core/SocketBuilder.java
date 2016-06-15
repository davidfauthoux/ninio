package com.davfx.ninio.core;

@Deprecated
public interface SocketBuilder<T> {
	T failing(Failing failing);
	T closing(Closing closing);
	T connecting(Connecting connecting);
	T receiving(Receiver receiver);
}
