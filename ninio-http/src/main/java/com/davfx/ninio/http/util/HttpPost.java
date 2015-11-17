package com.davfx.ninio.http.util;

import java.io.IOException;
import java.io.InputStream;

public interface HttpPost {
	InputStream open() throws IOException;
}
