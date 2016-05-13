package com.davfx.ninio.ping.v3;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connecting;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.core.v3.NinioSocketBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.Receiver;
import com.google.common.primitives.Doubles;
import com.savarese.rocksaw.net.Ping;
import com.savarese.rocksaw.net.PingHandler;

public final class PingSocket implements Connector {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PingSocket.class);

	public static interface Builder extends NinioSocketBuilder<Builder> {
	}

	public static Builder builder() {
		return new Builder() {
			private Connecting connecting = null;
			private Closing closing = null;
			private Failing failing = null;
			private Receiver receiver = null;
			
			@Override
			public Builder closing(Closing closing) {
				this.closing = closing;
				return this;
			}
		
			@Override
			public Builder connecting(Connecting connecting) {
				this.connecting = connecting;
				return this;
			}
			
			@Override
			public Builder failing(Failing failing) {
				this.failing = failing;
				return this;
			}
			
			@Override
			public Builder receiving(Receiver receiver) {
				this.receiver = receiver;
				return this;
			}
			
			@Override
			public Connector create(Queue ignoredQueue) {
				return new PingSocket(connecting, closing, failing, receiver);
			}
		};
	}
	
	private final Ping ping;
	
	public PingSocket(final Connecting connecting, final Closing closing, final Failing failing, final Receiver receiver) {
		Ping p;
		try {
			p = new Ping(new PingHandler() {
				@Override
				public void pong(InetAddress from, double time, double ttl) {
					ByteBuffer b = ByteBuffer.allocate(Doubles.BYTES);
					b.putDouble(time);
					b.flip();
					receiver.received(PingSocket.this, new Address(from.getHostAddress(), 0), b);
				}
			});
		} catch (IOException e) {
			LOGGER.error("Could not create ping manager", e);
			p = null;
		}
		ping = p;
	}
			
	@Override
	public void close() {
		if (ping == null) {
			return;
		}
		ping.close();
	}
	
	@Override
	public Connector send(Address address, ByteBuffer buffer) {
		if (ping == null) {
			return this;
		}
		try {
			ping.ping(InetAddress.getByName(address.getHost()));
		} catch (IOException e) {
			LOGGER.error("Could not ping {}", address, e);
		}
		return this;
	}
}
