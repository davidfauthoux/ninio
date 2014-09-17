package com.davfx.ninio.http;

import java.io.IOException;

public interface HttpServerHandlerFactory {
	HttpServerHandler create();

	void closed();
	void failed(IOException e);
}