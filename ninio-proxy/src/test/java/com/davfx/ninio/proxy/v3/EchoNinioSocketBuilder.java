package com.davfx.ninio.proxy.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.NinioSocketBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.google.common.base.Charsets;

public final class EchoNinioSocketBuilder implements NinioSocketBuilder<Void> {
	private Receiver receiver;
	
	@Override
	public Void closing(Closing closing) {
		return null;
	}
	@Override
	public Void connecting(Connecting connecting) {
		return null;
	}
	@Override
	public Void failing(Failing failing) {
		return null;
	}
	
	@Override
	public Void receiving(Receiver receiver) {
		this.receiver = receiver;
		return null;
	}
	
	@Override
	public Connector create(Queue queue) {
		final Receiver r = receiver;
		return new Connector() {
			@Override
			public void close() {
			}
			@Override
			public Connector send(Address address, ByteBuffer buffer) {
				String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
				r.received(this, address, ByteBuffer.wrap(("ECHO " + s).getBytes(Charsets.UTF_8)));
				return this;
			}
		};
	}

}
