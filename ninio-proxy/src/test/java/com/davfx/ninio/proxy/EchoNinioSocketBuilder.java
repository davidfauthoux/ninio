package com.davfx.ninio.proxy;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connecter;
import com.davfx.ninio.core.Connection;
import com.davfx.ninio.core.NinioBuilder;
import com.davfx.ninio.core.NinioProvider;
import com.davfx.ninio.core.SendCallback;
import com.google.common.base.Charsets;

public final class EchoNinioSocketBuilder implements NinioBuilder<Connecter> {
	public EchoNinioSocketBuilder() {
	}
	
	@Override
	public Connecter create(NinioProvider ninioProvider) {
		return new Connecter() {
			private Connection connection;
			
			@Override
			public void close() {
			}
			@Override
			public void connect(Connection connection) {
				this.connection = connection;
			}
			@Override
			public void send(Address address, ByteBuffer buffer, SendCallback callback) {
				String s = new String(buffer.array(), buffer.position(), buffer.remaining(), Charsets.UTF_8);
				connection.received(address, ByteBuffer.wrap(("ECHO " + s).getBytes(Charsets.UTF_8)));
			}
		};
	}

}
