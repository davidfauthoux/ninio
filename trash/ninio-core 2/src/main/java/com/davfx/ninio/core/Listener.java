package com.davfx.ninio.core;

public interface Listener extends Disconnectable {
	void listen(Listening listening);
}
