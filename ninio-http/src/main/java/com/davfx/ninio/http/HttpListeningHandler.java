package com.davfx.ninio.http;

import com.davfx.ninio.core.Closing;
import com.davfx.ninio.core.Connecting;
import com.davfx.ninio.core.Failing;

public interface HttpListeningHandler extends Closing, Failing, Connecting {
	interface HttpResponseSender {
		HttpContentSender send(HttpResponse response);
	}
	
	HttpContentReceiver handle(HttpRequest request, HttpResponseSender responseSender);
}
