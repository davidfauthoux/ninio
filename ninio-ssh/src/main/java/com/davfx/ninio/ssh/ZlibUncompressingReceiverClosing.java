package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Receiver;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

final class ZlibUncompressingReceiverClosing implements Receiver, Closing {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ZlibUncompressingReceiverClosing.class);
	
	private static final Config CONFIG = ConfigUtils.load(ZlibUncompressingReceiverClosing.class);
	private static final int BUFFER_SIZE = CONFIG.getBytes("zlib.buffer").intValue();

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
	public void received(Address address, ByteBuffer deflated) {
		if (!activated) {
			wrappee.received(address, deflated);
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
					LOGGER.error("Could not inflate", e);
					return;
				}
				inflated.flip();
				wrappee.received(address, inflated);
			}
		}
	}
	
	@Override
	public void closed() {
		closing.closed();
	}
}
