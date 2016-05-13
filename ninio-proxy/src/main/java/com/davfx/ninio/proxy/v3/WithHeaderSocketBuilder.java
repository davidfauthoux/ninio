package com.davfx.ninio.proxy.v3;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.NinioSocketBuilder;

public interface WithHeaderSocketBuilder extends NinioSocketBuilder<WithHeaderSocketBuilder> {
	WithHeaderSocketBuilder header(String header);
	WithHeaderSocketBuilder with(Address address);
}