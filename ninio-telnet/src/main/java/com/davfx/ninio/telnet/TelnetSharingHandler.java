package com.davfx.ninio.telnet;

import com.davfx.ninio.core.Closeable;
import com.davfx.ninio.core.Failable;

public interface TelnetSharingHandler extends AutoCloseable, Closeable {
	interface Callback extends Failable {
		void handle(String response);
	}
	
	void init(String prompt, String command, Callback callback);
	void write(String prompt, String command, Callback callback);
}
