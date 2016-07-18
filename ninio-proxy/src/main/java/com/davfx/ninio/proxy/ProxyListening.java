package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.NinioBuilder;

public interface ProxyListening extends Closing, Failing, Connecting {
	NinioBuilder<Connecter> create(Address address, String header);
}
