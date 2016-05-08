package com.davfx.ninio.proxy.v3;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.NinioSocketBuilder;

public interface ProxyListening {
	NinioSocketBuilder<?> create(Address address, String header);
}
