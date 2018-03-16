package com.davfx.ninio.core.v4;

import java.io.IOException;
import java.nio.channels.SocketChannel;

public interface SocketChannelProvider {
	SocketChannel open() throws IOException;
}
