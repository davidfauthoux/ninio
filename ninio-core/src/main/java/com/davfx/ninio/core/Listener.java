package com.davfx.ninio.core;

public interface Listener extends Disconnectable {
	interface Callback extends Connecting, Failing, Closing {
		Connection connecting(Connected connecting);
	}
	
	void listen(Callback callback);
}
