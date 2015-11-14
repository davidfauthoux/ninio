package com.davfx.ninio.http.util;

import java.io.OutputStream;

public interface HttpServiceResult {
	void success(String contentType, String content);
	OutputStream success(String contentType);
}