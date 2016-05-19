package com.davfx.ninio.core.v3;

import com.davfx.ninio.core.Address;

public interface Listening {
	void connecting(Address from, Connector connector, SocketBuilder<?> builder);
}