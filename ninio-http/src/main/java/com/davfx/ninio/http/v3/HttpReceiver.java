package com.davfx.ninio.http.v3;

import com.davfx.ninio.core.v3.Disconnectable;

public interface HttpReceiver {
	HttpContentReceiver received(Disconnectable disconnectable, HttpResponse response);
}
