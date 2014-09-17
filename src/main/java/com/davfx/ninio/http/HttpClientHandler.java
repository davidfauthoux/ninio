package com.davfx.ninio.http;

import com.davfx.ninio.common.ByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;

public interface HttpClientHandler extends FailableCloseableByteBufferHandler {
	void ready(ByteBufferHandler write);
	void received(HttpResponse response);
}
