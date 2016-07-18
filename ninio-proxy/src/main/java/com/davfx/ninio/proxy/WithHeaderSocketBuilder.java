package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.NinioBuilder;

public interface WithHeaderSocketBuilder extends NinioBuilder<Connecter> {
	WithHeaderSocketBuilder header(Header header);
	WithHeaderSocketBuilder with(Address address);
}