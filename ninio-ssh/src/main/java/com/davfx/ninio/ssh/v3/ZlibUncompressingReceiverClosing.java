package com.davfx.ninio.ssh.v3;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.v3.Closing;
import com.davfx.ninio.core.v3.Connector;
import com.davfx.ninio.core.v3.Receiver;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

final class ZlibUncompressingReceiverClosing implements Receiver, Closing {
	private static final Config CONFIG = ConfigFactory.load(ZlibUncompressingReceiverClosing.class.getClassLoader());
	private static final int BUFFER_SIZE = CONFIG.getBytes("ninio.ssh.zlib.buffer").intValue();

	private final Inflater inflater = new Inflater();
	private boolean activated = false;
	private final Receiver wrappee;
	private final Closing closing;

	public ZlibUncompressingReceiverClosing(Receiver wrappee, Closing closing) {
		this.wrappee = wrappee;
		this.closing = closing;
	}

	public void init() {
		activated = true;
	}

	@Override
	public void received(Connector connector, Address address, ByteBuffer deflated) {
		if (!activated) {
			wrappee.received(connector, address, deflated);
			return;
		}

		int r = deflated.remaining();

		if (r > 0) {
			inflater.setInput(deflated.array(), deflated.position(), r);
			deflated.position(deflated.position() + r);

			while (true) { // !inflater.needsInput() && !inflater.finished()) {
				ByteBuffer inflated = ByteBuffer.allocate(BUFFER_SIZE);
				try {
					int c = inflater.inflate(inflated.array(), inflated.position(), inflated.remaining());
					if (c == 0) {
						break;
					}
					inflated.position(inflated.position() + c);
				} catch (DataFormatException e) {
					throw new RuntimeException("Could not inflate", e);
				}
				inflated.flip();
				wrappee.received(connector, address, inflated);
			}
		}
	}
	
	@Override
	public void closed() {
		closing.closed();
	}
}
