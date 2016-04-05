package com.davfx.ninio.core.v3;

public interface Acceptable {
	Acceptable accepting(Accepting accepting);
	Acceptable accept(ListenConnectingable listening);
	Acceptable close();
	Acceptable failing(Failing failing);
}
