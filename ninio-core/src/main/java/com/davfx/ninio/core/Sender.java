package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Sender {
	interface Callback {
		void sent();
		void failed(IOException ioe);
	}
	
	void send(Address address, ByteBuffer buffer, Callback callback);
}
