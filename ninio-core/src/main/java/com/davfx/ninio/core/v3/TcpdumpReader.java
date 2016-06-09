package com.davfx.ninio.core.v3;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public interface TcpdumpReader {
	Iterable<String> tcpdumpOptions();
	
	interface Handler {
		void handle(double timestamp, Address source, Address destination, ByteBuffer buffer);
	}
	
	void read(InputStream input, Handler handler) throws IOException;
}
