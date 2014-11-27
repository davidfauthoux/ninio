package com.davfx.ninio.ping.util;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;

@Deprecated
public interface OldSimplePingClientHandler extends Closeable, Failable {
	interface Callback extends Closeable {
		interface PingCallback extends Failable {
			void pong(double time);
		}
		void ping(String host, double timeout, PingCallback callback);
	}
	void launched(Callback callback);
}
