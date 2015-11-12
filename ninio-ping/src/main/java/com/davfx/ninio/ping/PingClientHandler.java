package com.davfx.ninio.ping;

import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Failable;

public interface PingClientHandler extends Closeable, Failable {
	interface Callback extends Closeable {
		interface PingCallback extends Failable {
			void pong(double time);
		}
		void ping(String host, PingCallback callback);
	}
	void launched(Callback callback);
}
