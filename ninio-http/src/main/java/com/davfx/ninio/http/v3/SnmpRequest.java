package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.v3.Failing;
import com.davfx.ninio.http.HttpRequest;

public interface SnmpRequest {
	interface Send {
		void post(ByteBuffer buffer);
		void finish();
		void cancel();
	}
	Send create(HttpRequest request);
	SnmpRequest failing(Failing failing);
	SnmpRequest receiving(SnmpReceiver receiver);
}
