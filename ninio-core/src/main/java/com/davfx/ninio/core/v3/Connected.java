package com.davfx.ninio.core.v3;

public interface Connected extends Sender {
	void connecting(Connecting connecting);
	void receiving(Receiver receiver);
	void failing(Failing failing);
	void closing(Closing closing);
}
