package com.davfx.ninio.core;

public interface ReadyConnection extends FailableCloseableByteBufferHandler {
	void connected(FailableCloseableByteBufferHandler write);
}
