package com.davfx.ninio.telnet;

import com.davfx.ninio.core.Failable;

public interface TelnetSharingHandler {
	interface Callback extends Failable {
		void handle(String response);
	}
	
	void init(String command, String prompt, Callback callback);
	void write(String command, String prompt, Callback callback);
}
