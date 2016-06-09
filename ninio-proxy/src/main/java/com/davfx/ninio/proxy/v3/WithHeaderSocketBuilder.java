package com.davfx.ninio.proxy.v3;

import com.davfx.ninio.core.v3.Address;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Receiver;

public interface WithHeaderSocketBuilder extends NinioBuilder<Connector> {
	WithHeaderSocketBuilder header(String header);
	WithHeaderSocketBuilder with(Address address);
	WithHeaderSocketBuilder failing(Failing failing);
	WithHeaderSocketBuilder closing(Closing closing);
	WithHeaderSocketBuilder connecting(Connecting connecting);
	WithHeaderSocketBuilder receiving(Receiver receiver);
}