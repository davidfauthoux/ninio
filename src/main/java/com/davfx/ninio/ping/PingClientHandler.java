package com.davfx.ninio.ping;

import com.davfx.ninio.common.Closeable;
import com.davfx.ninio.common.Failable;

public interface PingClientHandler extends Closeable, Failable {
	int VALID_STATUS = 0;
	
	interface Callback extends Closeable {
		interface PingCallback extends Failable {
			void pong(int[] statuses, double[] times);
		}
		void ping(PingableAddress address, int numberOfRetries, double timeBetweenRetries, double retryTimeout, PingCallback callback);
	}
	void launched(Callback callback);
}
