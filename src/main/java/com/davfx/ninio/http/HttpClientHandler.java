package com.davfx.ninio.http;

import com.davfx.ninio.common.CloseableByteBufferHandler;
import com.davfx.ninio.common.FailableCloseableByteBufferHandler;

public interface HttpClientHandler extends FailableCloseableByteBufferHandler {
	void ready(CloseableByteBufferHandler write);
	void received(HttpResponse response);
}
