package com.davfx.ninio.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

interface TcpdumpReader {
	Iterable<String> tcpdumpOptions();
	
	interface Handler {
		void handle(double timestamp, Address source, Address destination, ByteBuffer buffer);
	}
	
	void read(InputStream input, int maxSize, Handler handler) throws IOException;
}
