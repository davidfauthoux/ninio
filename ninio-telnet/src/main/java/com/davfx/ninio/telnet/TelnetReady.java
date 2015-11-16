package com.davfx.ninio.telnet;

import com.davfx.ninio.core.ReadyConnection;

public interface TelnetReady {
	void connect(ReadyConnection clientHandler);
}
