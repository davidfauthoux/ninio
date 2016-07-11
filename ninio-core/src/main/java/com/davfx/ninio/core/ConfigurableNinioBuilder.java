package com.davfx.ninio.core;

public interface ConfigurableNinioBuilder<T, Builder> extends NinioBuilder<T> {
	Builder failing(Failing failing);
	Builder closing(Closing closing);
	Builder connecting(Connecting connecting);
	Builder receiving(Receiver receiver);
	Builder buffering(Buffering buffering);
}
