package com.davfx.ninio.core;

public interface Listener extends Disconnectable {
	
	interface ListenerConnecting extends Connection {
		void connecting(Connected connecting);
	}
	interface Callback extends Connecting, Failing, Closing {
		ListenerConnecting connecting();
	}
	
	void listen(Callback callback);
}
