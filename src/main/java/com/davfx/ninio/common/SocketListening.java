package com.davfx.ninio.common;


public interface SocketListening extends Closeable, Failable {
	CloseableByteBufferHandler connected(Address address, CloseableByteBufferHandler connection);
}
