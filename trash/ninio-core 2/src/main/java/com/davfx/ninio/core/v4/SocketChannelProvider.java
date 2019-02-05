package com.davfx.ninio.core.v4;

import java.io.IOException;
import java.nio.channels.SocketChannel;

interface SocketChannelProvider {
	SocketChannel open() throws IOException;
}
