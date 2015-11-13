package com.davfx.ninio.http;

import com.davfx.ninio.core.CloseableByteBufferHandler;
import com.davfx.ninio.core.FailableCloseableByteBufferHandler;

public interface HttpClientHandler extends FailableCloseableByteBufferHandler {
	void ready(CloseableByteBufferHandler write);
	void received(HttpResponse response);
}
