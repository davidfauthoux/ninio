package com.davfx.ninio.ping;

import java.io.IOException;

import com.davfx.ninio.core.Address;

public interface PingConnecter {
	interface Connecting extends AutoCloseable {
		void close();

		interface Callback {
			void received(double time);
			void failed(IOException ioe);
		}
		
		interface Cancelable {
			void cancel();
		}

		Cancelable ping(String host, Callback callback);
	}
	
	interface Callback {
		void connected(Address address);
		void failed(IOException ioe);
		void closed();
	}
	
	Connecting connect(Callback callback);
}
