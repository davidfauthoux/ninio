package com.davfx.ninio.core.v3;

public interface Acceptable extends Failingable {
	void accepting(Accepting accepting);
	void accept(ListenConnectingable listening);
	void close();
}
