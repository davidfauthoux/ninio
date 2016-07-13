package com.davfx.ninio.core;

import java.io.IOException;

public interface Listener {
	interface Listening extends AutoCloseable {
		void close();
	}
	
	interface Callback {
		void connected();
		void failed(IOException ioe);
		void closed();

		
		interface Connecting extends Connecter.Callback {
			void connecting(Address from, Connecter.Connecting connecting);
		}
		Connecting connecting();
	}
	
	Listening listen(Callback callback);
}
