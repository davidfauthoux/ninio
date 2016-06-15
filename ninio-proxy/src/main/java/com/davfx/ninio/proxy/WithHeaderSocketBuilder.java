package com.davfx.ninio.proxy;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.core.Failing;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.Receiver;

public interface WithHeaderSocketBuilder extends NinioBuilder<Connector> {
	WithHeaderSocketBuilder header(String header);
	WithHeaderSocketBuilder with(Address address);
	WithHeaderSocketBuilder failing(Failing failing);
	WithHeaderSocketBuilder closing(Closing closing);
	WithHeaderSocketBuilder connecting(Connecting connecting);
	WithHeaderSocketBuilder receiving(Receiver receiver);
}