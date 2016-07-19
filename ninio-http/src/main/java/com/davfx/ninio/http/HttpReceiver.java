package com.davfx.ninio.http;

import com.davfx.ninio.core.Failing;

public interface HttpReceiver extends Failing {
	HttpContentReceiver received(HttpResponse response);
}
