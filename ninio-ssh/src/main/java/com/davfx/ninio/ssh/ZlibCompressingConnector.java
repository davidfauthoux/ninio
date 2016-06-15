package com.davfx.ninio.ssh;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

import com.davfx.ninio.core.Address;
import com.davfx.ninio.core.Connector;
import com.davfx.ninio.util.ConfigUtils;
import com.typesafe.config.Config;

final class ZlibCompressingConnector implements Connector {
	private static final Config CONFIG = ConfigUtils.load(ZlibCompressingConnector.class);
	private static final int BUFFER_SIZE = CONFIG.getBytes("zlib.buffer").intValue();

	private final Connector wrappee;
	private final Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
	private boolean activated = false;

	public ZlibCompressingConnector(Connector wrappee) {
		this.wrappee = wrappee;
	}

	public void init() {
		activated = true;
	}

	@Override
	public Connector send(Address address, ByteBuffer buffer) {
		if (!activated) {
			wrappee.send(address, buffer);
			return this;
		}
		deflater.setInput(buffer.array(), buffer.position(), buffer.remaining());
		buffer.position(buffer.limit());
		write(address);
		return this;
	}

	@Override
	public void close() {
		wrappee.close();
	}

	private void write(Address address) {
		while (true) {
			int offset = SshSpecification.OPTIMIZATION_SPACE;
			ByteBuffer deflated = ByteBuffer.allocate(BUFFER_SIZE);
			deflated.position(offset);
			int c = deflater.deflate(deflated.array(), deflated.position(), deflated.remaining(), Deflater.SYNC_FLUSH);
			if (c == 0) {
				break;
			}
			deflated.position(deflated.position() + c);
			deflated.flip();
			deflated.position(offset);
			wrappee.send(address, deflated);
		}
	}
}
