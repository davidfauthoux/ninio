package com.davfx.ninio.core;

public interface SocketListening extends Closeable, Failable {
	interface Listening extends Closeable {
		void disconnect();
	}
	void listening(Listening listening);
	CloseableByteBufferHandler connected(Address address, CloseableByteBufferHandler connection);
}
