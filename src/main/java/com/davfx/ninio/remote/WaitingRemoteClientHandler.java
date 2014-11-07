package com.davfx.ninio.remote;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;

public interface WaitingRemoteClientHandler extends Closeable, Failable {
	interface Callback extends Closeable {
		interface SendCallback extends Failable {
			void received(String text);
		}
		void send(String line, SendCallback callback);
	}
	void launched(String init, Callback callback);
}
