package com.davfx.ninio.ping.v3;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.core.v3.NinioBuilder;
import com.davfx.ninio.core.v3.Queue;
import com.davfx.ninio.core.v3.RawSocket;
import com.davfx.ninio.core.v3.Receiver;

public final class PingClient implements Disconnectable {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PingClient.class);
	
	private static final int ICMP_PROTOCOL = 1;
	
	public static interface Builder extends NinioBuilder<PingClient> {
		Builder receiving(PingReceiver receiver);
		Builder with(RawSocket.Builder connectorFactory);
	}
	
	public static Builder builder() {
		return new Builder() {
			private PingReceiver receiver = null;
			private RawSocket.Builder connectorFactory = RawSocket.builder();
			
			@Override
			public Builder receiving(PingReceiver receiver) {
				this.receiver = receiver;
				return this;
			}

			@Override
			public Builder with(RawSocket.Builder connectorFactory) {
				this.connectorFactory = connectorFactory;
				return this;
			}

			@Override
			public PingClient create(Queue queue) {
				return new PingClient(queue, connectorFactory, receiver);
			}
		};
	}
	
	private final Connector connector;

	public PingClient(Queue queue, RawSocket.Builder connectorFactory, final PingReceiver receiver) {
		connector = connectorFactory.receiving(new Receiver() {
			@Override
			public void received(Connector connector, Address address, ByteBuffer buffer) {
				long now = System.nanoTime();
				long time;
				try {
					buffer.get(); // type
					buffer.get(); // code
					buffer.getShort(); // checksum
					buffer.getShort(); // identifier
					buffer.getShort(); // sequence
					time = buffer.getLong();
				} catch (Exception e) {
					LOGGER.error("Invalid packet", e);
					return;
				}

				double delta = (now - time) / 1_000_000_000d;
				
				if (receiver != null) {
					receiver.received(address.getHost(), delta);
				}
			}
		}).protocol(ICMP_PROTOCOL).create(queue);
	}
	
	@Override
	public void close() {
		connector.close();
	}
	
	public void ping(String host) {
		byte[] sendData = new byte[16];

		ByteBuffer b = ByteBuffer.wrap(sendData);
		b.put((byte) 8); // requestType (Echo)
		b.put((byte) 0); // code
		int checksumPosition = b.position();
		b.putShort((short) 0); // checksum
		b.putShort((short) (65535)); // identifier
		b.putShort((short) (10)); // sequence
		long nt = System.nanoTime();
		b.putLong(nt);
		int endPosition = b.position();

		b.position(0);
		int checksum = 0;
		while (b.position() < endPosition) {
			checksum += b.getShort() & 0xFFFF;
		}
	    while((checksum & 0xFFFF0000) != 0) {
	    	checksum = (checksum & 0xFFFF) + (checksum >>> 16);
	    }

	    checksum = (~checksum & 0xffff);
		b.position(checksumPosition);
		b.putShort((short) (checksum & 0xFFFF));
		b.position(endPosition);
		b.flip();
		
		connector.send(new Address(host, 0), b);
	}
}
