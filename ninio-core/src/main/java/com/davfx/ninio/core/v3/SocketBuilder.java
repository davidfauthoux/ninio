package com.davfx.ninio.core.v3;

import java.util.concurrent.Executor;

public interface SocketBuilder<T> extends NinioBuilder<Connector> {
	T with(Executor executor);

	T failing(Failing failing);
	T closing(Closing closing);
	T connecting(Connecting connecting);
	T receiving(Receiver receiver);
}
