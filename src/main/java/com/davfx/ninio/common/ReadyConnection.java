package com.davfx.ninio.common;


public interface ReadyConnection extends FailableCloseableByteBufferHandler {
	void connected(FailableCloseableByteBufferHandler write);
}
