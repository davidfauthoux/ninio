package com.davfx.ninio.core;

import java.io.IOException;
import java.nio.ByteBuffer;

public interface Connecter {
	interface Connecting extends AutoCloseable {
		void close();

		interface Callback {
			void sent();
			void failed(IOException ioe);
		}
		
		void send(Address address, ByteBuffer buffer, Callback callback);
	}
	
	interface Callback {
		void connected(Address address);
		void failed(IOException ioe);
		void closed();
		void received(Address address, ByteBuffer buffer);
	}
	
	Connecting connect(Callback callback);
}
