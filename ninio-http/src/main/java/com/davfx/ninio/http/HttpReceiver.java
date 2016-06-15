package com.davfx.ninio.http;

import com.davfx.ninio.core.Disconnectable;

public interface HttpReceiver {
	HttpContentReceiver received(Disconnectable disconnectable, HttpResponse response);
}
