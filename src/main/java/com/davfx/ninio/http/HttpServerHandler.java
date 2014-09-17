package com.davfx.ninio.http;

import com.davfx.ninio.common.FailableCloseableByteBufferHandler;

public interface HttpServerHandler extends FailableCloseableByteBufferHandler {
	interface Write extends FailableCloseableByteBufferHandler {
		void write(HttpResponse response);
	}
	
	void handle(HttpRequest request);
	void ready(Write write);
}