package com.davfx.ninio.http.v3;

import java.nio.ByteBuffer;

import com.davfx.ninio.core.v3.Disconnectable;
import com.davfx.ninio.http.HttpResponse;

public interface HttpReceiver {
	interface ContentReceiver {
		void received(ByteBuffer buffer);
		void ended();
	}
	ContentReceiver received(Disconnectable disconnectable, HttpResponse response);
}
