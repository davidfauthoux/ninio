package com.davfx.ninio.telnet;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;

public interface TelnetClientHandler extends Closeable, Failable {
	interface Callback extends Closeable {
		void send(String text);
	}
	void launched(Callback callback);
	void received(String text); // May be called with empty text to awake processing
}
