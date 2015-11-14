package com.davfx.ninio.http.util;

import java.nio.ByteBuffer;

public interface HttpServiceWrite {
	void write(ByteBuffer buffer);
}