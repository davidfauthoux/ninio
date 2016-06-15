package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Receiver;

public interface ProxyListening {
	interface Builder extends NinioBuilder<Connector> {
		Builder failing(Failing failing);
		Builder closing(Closing closing);
		Builder connecting(Connecting connecting);
		Builder receiving(Receiver receiver);
	}
	Builder create(Address address, String header);
}
