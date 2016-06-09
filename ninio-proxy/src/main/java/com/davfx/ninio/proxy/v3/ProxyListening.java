package com.davfx.ninio.proxy.v3;

import com.davfx.ninio.core.v3.Address;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Receiver;

public interface ProxyListening {
	interface Builder extends NinioBuilder<Connector> {
		Builder failing(Failing failing);
		Builder closing(Closing closing);
		Builder connecting(Connecting connecting);
		Builder receiving(Receiver receiver);
	}
	Builder create(Address address, String header);
}
