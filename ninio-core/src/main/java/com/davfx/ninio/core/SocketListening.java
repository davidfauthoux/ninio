package com.davfx.ninio.core;

public interface SocketListening extends Closeable, Failable {
	void listening(Closeable closeable);
	CloseableByteBufferHandler connected(Address address, CloseableByteBufferHandler connection);
}
