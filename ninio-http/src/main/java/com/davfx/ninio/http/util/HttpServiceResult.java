package com.davfx.ninio.http.util;

import java.io.OutputStream;

public interface HttpServiceResult {
	HttpServiceResult contentType(String contentType);
	HttpServiceResult status(int status, String reason);
	void out(String content);
	OutputStream out();
}