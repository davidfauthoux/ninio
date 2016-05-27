package com.davfx.ninio.core.v3;

import com.davfx.ninio.core.Address;

public interface Connecting {
	void connected(Address to, Connector connector);
}
