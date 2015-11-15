package com.davfx.ninio.http.util;

import java.io.OutputStream;

public interface HttpServiceResult {
	HttpServiceResult contentType(String contentType);
	void success(String content);
	OutputStream success();
}